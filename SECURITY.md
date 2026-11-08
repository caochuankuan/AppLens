# Security & responsible use

AppLens is a local Android inspection tool. It uses Shizuku to access app data
and scans APKs for hard-coded URLs. It is intended only for devices and
applications you own or are explicitly authorized to inspect.

- Do not use AppLens to access, collect, or distribute other people's private data.
- Follow local law and the target application's terms of service.
- Database access requires a root-level Shizuku server; this is enforced by
  Android's security model, not by AppLens.
- AppLens does not request network permission and does not upload scanned data.

If you find a security issue, please report it privately through GitHub Security
Advisories rather than opening a public issue with sensitive details.
