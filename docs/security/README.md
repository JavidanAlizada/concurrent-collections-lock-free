# Security

Security considerations specific to this library — mainly memory safety
under concurrent access (a broken CAS loop or missed happens-before edge is
this project's actual security-relevant failure mode, not auth/network
concerns a library with no I/O doesn't have). See also `SECURITY.md` at the
repo root for the vulnerability reporting process.
