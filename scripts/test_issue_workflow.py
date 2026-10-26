"""Regression checks for journal isolation, resume safety, and preflight failures."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import issue_workflow as workflow


class JournalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-qm", "Initial")
        self.git("remote", "add", "origin", "git@github.com:example/project.git")
        self.cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.cwd)

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True, text=True, capture_output=True).stdout.strip()

    def init(self, issue="https://github.com/example/project/issues/12", base="HEAD"):
        return workflow.initialize(argparse.Namespace(issue=issue, base=base, runs_dir=None))

    def test_journal_is_outside_tracked_files_and_duplicate_is_refused(self):
        result = self.init()
        self.assertEqual(self.git("status", "--porcelain"), "")
        self.assertEqual(result["state"]["base_sha"], self.git("rev-parse", "HEAD"))
        with self.assertRaisesRegex(ValueError, "resume"):
            self.init()

    def test_wrong_repository_and_option_like_base_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "match"):
            self.init("https://github.com/another/project/issues/12")
        with self.assertRaises(ValueError):
            self.init(base="--help")

    def test_initial_write_failure_does_not_claim_run(self):
        with patch.object(workflow, "atomic_json", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                self.init()
        result = self.init()
        self.assertTrue((Path(result["run_dir"]) / "state.json").is_file())

    def test_concurrent_initialization_has_one_winner(self):
        def attempt(_):
            try:
                return self.init()["run_dir"]
            except ValueError:
                return None

        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(attempt, range(8)))
        self.assertEqual(sum(item is not None for item in results), 1)

    def test_url_is_data_not_shell_input(self):
        for url in ("https://github.com/example/project/issues/12?x=1", "12", "https://github.com/example/project/issues/12;touch BAD"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                workflow.issue_identity(url)
        self.assertFalse((self.root / "BAD").exists())

    def test_https_remote_supported_without_persisting_remote_url(self):
        self.git("remote", "set-url", "origin", "https://github.com/example/project.git")
        result = self.init()
        self.assertEqual(result["state"]["repository"], "example/project")
        self.assertNotIn("origin", result["state"])

    def test_concurrent_events_are_not_lost(self):
        run = self.init()["run_dir"]

        def append(index):
            workflow.record(argparse.Namespace(run_dir=run, stage="investigation", status="in_progress", evidence=f"artifact-{index}.md"))

        with ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(append, range(12)))
        state = json.loads((Path(run) / "state.json").read_text())
        self.assertEqual(len(state["events"]), 12)
        self.assertEqual(len({event["evidence"] for event in state["events"]}), 12)

    def test_worktrees_share_issue_identity(self):
        run = self.init()["run_dir"]
        worktree = self.root / "checkout"
        self.git("worktree", "add", "--detach", str(worktree), "HEAD")
        os.chdir(worktree)
        with self.assertRaisesRegex(ValueError, run):
            self.init()

    def test_empty_evidence_leaves_journal_intact(self):
        run = self.init()["run_dir"]
        with self.assertRaises(ValueError):
            workflow.record(argparse.Namespace(run_dir=run, stage="review", status="passed", evidence="  "))
        state = json.loads((Path(run) / "state.json").read_text())
        self.assertEqual(state["events"], [])

    def test_missing_tools_fail_preflight(self):
        with patch.object(workflow, "command", return_value=(127, "")), patch.object(workflow.shutil, "which", return_value=None):
            report = workflow.preflight(argparse.Namespace(require_emulator=True, avd="Karoo"))
        self.assertFalse(report["passed"])
        required_failures = {item["name"] for item in report["checks"] if item["required"] and not item["passed"]}
        self.assertIn("github_auth", required_failures)
        self.assertIn("emulator_acceleration", required_failures)


if __name__ == "__main__":
    unittest.main()
