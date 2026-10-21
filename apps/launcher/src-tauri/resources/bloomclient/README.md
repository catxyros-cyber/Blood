CI places the BLOOD Client jars and `manifest.json` here before building the installer
(see `.github/workflows/release.yml`, job "bloom-client"). In local builds without the mod
this folder only contains this file, and the launcher shows BLOOD Client as "not included
in this build".
