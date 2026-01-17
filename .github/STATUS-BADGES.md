# GitHub Actions Status Badges

Add these to your [README.md](../README.md) for real-time CI/CD status:

## Badge Code

```markdown
![Build and Test](https://github.com/ZiggiZagga/IronBucket/actions/workflows/build-and-test.yml/badge.svg)
![Security Scan](https://github.com/ZiggiZagga/IronBucket/actions/workflows/security-scan.yml/badge.svg)
![Docker Build](https://github.com/ZiggiZagga/IronBucket/actions/workflows/docker-build.yml/badge.svg)
![SLSA Provenance](https://github.com/ZiggiZagga/IronBucket/actions/workflows/slsa-provenance.yml/badge.svg)
```

## Example Integration

Place at the top of README.md after the title:

```markdown
# IronBucket

![Build and Test](https://github.com/ZiggiZagga/IronBucket/actions/workflows/build-and-test.yml/badge.svg)
![Security Scan](https://github.com/ZiggiZagga/IronBucket/actions/workflows/security-scan.yml/badge.svg)
![Docker Build](https://github.com/ZiggiZagga/IronBucket/actions/workflows/docker-build.yml/badge.svg)

Production-ready S3-compatible microservices platform...
```

## Alternative: Compact Format

```markdown
[![CI/CD](https://github.com/ZiggiZagga/IronBucket/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/ZiggiZagga/IronBucket/actions)
```

## SLSA Badge

```markdown
[![SLSA 3](https://slsa.dev/images/gh-badge-level3.svg)](https://slsa.dev)
```
