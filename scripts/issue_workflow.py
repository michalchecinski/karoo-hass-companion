#!/usr/bin/env python3
"""Local capability checks and durable evidence journal for the issue-to-PR skill.

This is bookkeeping, not an autonomous runner or a verification authority.
No credentials, issue contents, or command output are persisted automatically.
"""

import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


STAGES = ("intake", "investigation", "plan", "implementation", "review", "verification", "publication")
STATUSES = (
    "in_progress", "passed", "needs_decision", "inconclusive", "cannot_reproduce",
    "blocked_environment", "already_done", "draft_pr", "ready_pr", "failed",
)


def now():
    return datetime.now(timezone.utc).isoformat()


def command(args, cwd=None, timeout=20):
    try:
        result = subprocess.run(args, cwd=cwd, text=True, capture_output=True, timeout=timeout)
        return result.returncode, result.stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return 127, ""


def git(*args):
    code, output = command(["git", *args])
    if code:
        raise ValueError("Git operation failed: " + " ".join(args))
    return output


def issue_identity(url):
    match = re.fullmatch(r"https://github\.com/([\w.-]+)/([\w.-]+)/issues/([1-9][0-9]*)/?", url)
    if not match:
        raise ValueError("Expected a github.com issue URL, without a query or fragment")
    owner, repo, number = match.groups()
    return owner, repo, number


def atomic_json(path, value):
    descriptor, temporary = tempfile.mkstemp(prefix=".state-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w") as output:
            json.dump(value, output, indent=2)
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def initialize(args):
    owner, repo, number = issue_identity(args.issue)
    root = Path(git("rev-parse", "--show-toplevel")).resolve()
    common = Path(git("rev-parse", "--git-common-dir")).resolve()
    base = git("rev-parse", "--verify", "--end-of-options", args.base + "^{commit}")
    origin = git("remote", "get-url", "origin")
    # Avoid recording remote URLs: an HTTPS remote can contain authentication.
    remote = re.fullmatch(r"(?:https://github\.com/|git@github\.com:)([\w.-]+)/([\w.-]+?)(?:\.git)?/?", origin)
    if not remote or tuple(s.lower() for s in remote.groups()) != (owner.lower(), repo.lower()):
        raise ValueError("Issue repository must match the github.com origin remote")
    runs = Path(args.runs_dir).expanduser().resolve() if args.runs_dir else common / "issue-workflow"
    runs.mkdir(parents=True, exist_ok=True, mode=0o700)
    run = runs / f"{owner.lower()}--{repo.lower()}--{number}"
    state = {
        "schema_version": 1, "issue": args.issue.rstrip("/"), "repository": f"{owner}/{repo}",
        "source_checkout": str(root), "base_ref": args.base, "base_sha": base,
        "created_at": now(), "stage": "intake", "status": "in_progress", "events": [],
    }
    # Publish only a complete initial journal. A failed write or interrupted
    # process may leave staging data, but cannot claim the final issue identity.
    with tempfile.TemporaryDirectory(prefix=".init-", dir=runs) as staging:
        candidate = Path(staging) / "run"
        candidate.mkdir(mode=0o700)
        atomic_json(candidate / "state.json", state)
        try:
            os.rename(candidate, run)
        except OSError as error:
            if run.exists():
                raise ValueError(f"Run already exists; inspect and resume it: {run}") from error
            raise
    return {"run_dir": str(run), "state": state}


def record(args):
    run = Path(args.run_dir).expanduser().resolve()
    if not args.evidence.strip():
        raise ValueError("Evidence must be nonempty; use sanitized artifact paths and conclusions")
    with (run / ".journal.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        path = run / "state.json"
        state = json.loads(path.read_text())
        if state.get("schema_version") != 1:
            raise ValueError("Unsupported run schema")
        state["stage"], state["status"] = args.stage, args.status
        state["events"].append({
            "at": now(), "stage": args.stage, "status": args.status, "evidence": args.evidence,
        })
        atomic_json(path, state)
    return state


def preflight(args):
    checks = []

    def check(name, passed, required=True, detail=""):
        checks.append({"name": name, "passed": bool(passed), "required": required, "detail": detail})

    for executable in ("git", "gh", "python3"):
        check(executable, shutil.which(executable))
    check("codex_cli", shutil.which("codex"), required=False,
          detail="Optional in Codex desktop; needed for a future CLI runner")
    auth_code, _ = command(["gh", "auth", "status", "--hostname", "github.com"])
    check("github_auth", auth_code == 0, detail="Authentication only; repository write access is checked during intake")
    # java -version writes to stderr; do not print environment or credential data.
    try:
        java = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=10)
        java_version = java.stdout + java.stderr
        java_code = java.returncode
    except (OSError, subprocess.TimeoutExpired):
        java_version = ""
        java_code = 127
    check("jdk_17", java_code == 0 and re.search(r'version "17[.\"]', java_version),
          detail="Use JDK 17, matching project CI")
    root_code, root_text = command(["git", "rev-parse", "--show-toplevel"])
    check("repository", root_code == 0)
    if root_code == 0:
        root = Path(root_text)
        check("gradle_wrapper", (root / "gradlew").is_file())
        sdk_candidates = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")]
        properties = root / "local.properties"
        if properties.is_file():
            for line in properties.read_text().splitlines():
                if line.startswith("sdk.dir="):
                    sdk_candidates.append(line.split("=", 1)[1].replace("\\:", ":").replace("\\ ", " "))
    else:
        sdk_candidates = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")]
    sdk_candidates += [str(Path.home() / "Library/Android/sdk"), str(Path.home() / "Android/Sdk")]
    sdk = next((Path(p) for p in sdk_candidates if p and Path(p).is_dir()), None)
    check("android_sdk", sdk is not None)
    if sdk:
        check("android_platforms", (sdk / "platforms").is_dir())
        check("android_build_tools", (sdk / "build-tools").is_dir())
    adb = str(sdk / "platform-tools/adb") if sdk else "adb"
    emulator = str(sdk / "emulator/emulator") if sdk else "emulator"
    check("adb", Path(adb).is_file() or shutil.which(adb), required=args.require_emulator)
    check("screenshot_cropper", shutil.which("sips"), required=args.require_emulator,
          detail="Current screenshot helper is macOS-only; replace cropper for Linux migration")
    accel_code, _ = command([emulator, "-accel-check"])
    check("emulator_acceleration", accel_code == 0, required=args.require_emulator)
    avd_code, avds = command([emulator, "-list-avds"])
    check("karoo_avd", avd_code == 0 and args.avd in avds.splitlines(), required=args.require_emulator,
          detail=f"Expected AVD: {args.avd}; boot/install/test/capture remain separate checks")
    return {"passed": all(c["passed"] for c in checks if c["required"]), "checks": checks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    pre = commands.add_parser("preflight", help="Read-only tool and SDK checks; does not boot an emulator")
    pre.add_argument("--require-emulator", action="store_true")
    pre.add_argument("--avd", default="Karoo")
    init = commands.add_parser("init", help="Create one journal per issue, shared across worktrees")
    init.add_argument("issue")
    init.add_argument("--base", required=True)
    init.add_argument("--runs-dir")
    rec = commands.add_parser("record", help="Append an agent-reported checkpoint, not a verified gate")
    rec.add_argument("run_dir")
    rec.add_argument("--stage", choices=STAGES, required=True)
    rec.add_argument("--status", choices=STATUSES, required=True)
    rec.add_argument("--evidence", required=True)
    show = commands.add_parser("show")
    show.add_argument("run_dir")
    args = parser.parse_args()
    try:
        if args.action == "preflight":
            result = preflight(args)
        elif args.action == "init":
            result = initialize(args)
        elif args.action == "record":
            result = record(args)
        else:
            result = json.loads((Path(args.run_dir).expanduser() / "state.json").read_text())
        print(json.dumps(result, indent=2))
        return 1 if args.action == "preflight" and not result["passed"] else 0
    except (ValueError, OSError, KeyError) as error:
        print(f"issue-workflow: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
