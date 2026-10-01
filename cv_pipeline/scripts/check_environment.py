"""Fail-fast diagnostics for the local computer-vision environment."""

from __future__ import annotations

import importlib
import platform
import sys
from importlib import metadata
from pathlib import Path

REQUIRED_PACKAGES = {
    "numpy": "numpy",
    "opencv-python": "cv2",
    "torch": "torch",
    "torchvision": "torchvision",
    "ultralytics": "ultralytics",
}
SUPPORTED_PYTHON = (3, 10) <= sys.version_info[:2] < (3, 15)


def pinned_versions(requirements_path: Path) -> dict[str, str]:
    pins = {}
    for raw_line in requirements_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "==" not in line:
            continue
        name, version = line.split("==", maxsplit=1)
        pins[name.lower()] = version
    return pins


def main() -> int:
    project_root = Path(__file__).resolve().parents[1]
    expected_venv = project_root / ".venv"
    expected_versions = pinned_versions(project_root / "requirements.txt")
    failures: list[str] = []

    print("TaticAnalytics — environment check")
    print(f"Python: {platform.python_version()} ({sys.executable})")
    print(f"Platform: {platform.platform()}")
    print(f"Virtual environment: {sys.prefix}")

    if not SUPPORTED_PYTHON:
        failures.append("Use Python 3.10-3.14; Python 3.12 is recommended")
    if Path(sys.prefix).resolve() != expected_venv.resolve():
        failures.append(f"Expected the project virtual environment at {expected_venv}")

    modules = {}
    for distribution, module_name in REQUIRED_PACKAGES.items():
        try:
            modules[module_name] = importlib.import_module(module_name)
            installed_version = metadata.version(distribution)
            print(f"{distribution}: {installed_version}")
            expected_version = expected_versions.get(distribution.lower())
            if expected_version and installed_version != expected_version:
                failures.append(
                    f"{distribution} is {installed_version}, expected {expected_version}; "
                    "reinstall requirements.txt"
                )
        except (ImportError, metadata.PackageNotFoundError) as error:
            failures.append(f"{distribution} is unavailable: {error}")

    torch = modules.get("torch")
    if torch is not None:
        if torch.cuda.is_available():
            accelerator = f"CUDA ({torch.cuda.get_device_name(0)})"
        elif getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
            accelerator = "Apple Metal (MPS)"
        else:
            accelerator = "CPU"
        print(f"PyTorch accelerator: {accelerator}")

    if failures:
        print("\nEnvironment is NOT ready:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("\nEnvironment is ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
