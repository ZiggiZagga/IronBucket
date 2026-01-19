# 🤝 Contributing to IronBucket

**Target Audience**: Developers, Architects, Community Contributors  
**Read Time**: 20 minutes  
**Status**: ✅ Complete  
**Last Updated**: January 15, 2026

---

## Welcome! 👋

Thank you for your interest in contributing to IronBucket! This document guides you through the process of contributing code, documentation, bug reports, and feature ideas.

**Our Values**:
- 🎯 **Security First**: Security implications reviewed in every PR
- 🔬 **Quality Matters**: Tests required, clear code preferred
- 📚 **Documentation Required**: Code changes must include doc updates
- 🤝 **Community Driven**: All contributors valued and respected
- ♿ **Inclusive**: Welcoming contributors of all experience levels

---

## 🚀 Getting Started

### Step 1: Fork & Setup Your Environment

```bash
# Fork the repository on GitHub
# Clone your fork
git clone https://github.com/YOUR-USERNAME/IronBucket.git
cd IronBucket

# Add upstream remote
git remote add upstream https://github.com/ZiggiZagga/IronBucket.git

# Create a feature branch
git checkout -b feature/your-feature-name
```

### Step 2: Install Development Dependencies

```bash
# Java 21 required
java -version

# Maven for building
mvn --version

# Docker & Docker Compose for local development
docker --version
docker-compose --version

# Node.js for testing shared libraries
node --version
npm --version
```

### Step 3: Run the Project Locally

```bash
# Start development environment
cd steel-hammer
docker-compose -f docker-compose-steel-hammer.yml up -d

# Verify all services started
docker-compose ps

# Check service health
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Policy Engine
curl http://localhost:8082/actuator/health  # S3 Proxy
curl http://localhost:8083/actuator/health  # Audit Logger

# Run tests
cd ../ironbucket-shared-testing
npm test
```

See: [START.md](START.md) for more details.

---

## 📋 Code of Conduct

We are committed to providing a welcoming and inclusive environment. All contributors are expected to:

1. **Be Respectful** - Treat all contributors with respect
2. **Be Constructive** - Provide helpful, thoughtful feedback
3. **Be Professional** - Keep discussions focused on technical merit
4. **Be Inclusive** - Welcome contributors of all backgrounds and experiences
5. **Be Responsible** - Take responsibility for your contributions

**Violations** can be reported to: [contact info to be added]

---

## 🛠️ Types of Contributions

### 🐛 Bug Reports

Found a bug? Please help us fix it!

**What to include**:
```
Title: [BUG] Brief description

Environment:
- OS: Ubuntu 24.04
- Java version: 21
- Docker version: 26.0.0
- Commit: abc123def456

Steps to reproduce:
1. Start with docker-compose up
2. Run test command...
3. Observe error

Expected behavior:
Service should return 200 OK with object metadata

Actual behavior:
Service returns 500 Internal Server Error

Logs:
[Paste relevant error logs]
```

**Quality checklist**:
- [x] Reproducible (you can repeat it)
- [x] Specific (not vague)
- [x] Includes environment details
- [x] Shows expected vs. actual behavior
- [x] Includes relevant logs/screenshots

### 💡 Feature Requests

Have an idea? We'd love to hear it!

**What to include**:
```
Title: [FEATURE] Brief description

Problem:
What problem does this solve?

Proposed Solution:
How should it work?

Alternative Approaches:
Any other ways to solve this?

Example Use Case:
When would users need this?

Acceptance Criteria:
- Feature does X
- Feature does Y
- Feature is documented
```

**Quality checklist**:
- [x] Clear problem statement
- [x] Specific, not vague
- [x] Aligned with project goals
- [x] Technical feasibility considered
- [x] No duplicates of existing issues

### 📚 Documentation Improvements

Documentation is crucial!

**What to contribute**:
- Clarify existing documentation
- Add examples and tutorials
- Fix broken links
- Add missing sections
- Improve diagrams
- Translate documentation

**How to submit**:
```bash
# Edit markdown files directly
# Test that examples work
# Submit PR with clear description
```

### 🧪 Test Improvements

More tests = better quality!

**What to contribute**:
- Unit test coverage (target: > 80%)
- Integration tests
- E2E test scenarios
- Performance benchmarks
- Error handling tests

**Java test structure** (Maven + JUnit):
```java
@SpringBootTest
class PolicyEngineTest {
    
    @Test
    void shouldEvaluateSimplePolicy() {
        // Arrange
        Policy policy = createTestPolicy();
        
        // Act
        Decision decision = policyEngine.evaluate(policy);
        
        // Assert
        assertEquals(ALLOW, decision);
    }
}
```

**JavaScript test structure** (Jest):
```javascript
describe('Policy Parser', () => {
    
    test('should parse allow policy', () => {
        const result = parsePolicy(simpleAllowPolicy);
        expect(result.effect).toBe('ALLOW');
    });
});
```

### 🔧 Code Contributions

Want to implement a feature or fix a bug?

**Process**:
1. Check existing issues first (don't duplicate)
2. Discuss in issue before starting (prevents wasted effort)
3. Follow code style guidelines
4. Write tests for new code
5. Document your changes
6. Submit PR with clear description

---

## 💻 Code Style & Standards

### Java Code Style

**Naming Conventions**:
```java
// Classes: PascalCase
public class PolicyEvaluator { }

// Methods: camelCase
public Decision evaluatePolicy() { }

// Constants: UPPER_SNAKE_CASE
public static final int MAX_POLICY_SIZE = 10_000;

// Variables: camelCase
String policyName = "read-public";
```

**Formatting**:
```java
// Use 4-space indentation
// Max line length: 100 characters
// One class per file
// Package private before public

// Good:
public class PolicyEngine {
    private static final Logger LOGGER = 
        LoggerFactory.getLogger(PolicyEngine.class);
    
    public Decision evaluate(Policy policy) {
        // Implementation
    }
}

// Bad:
public class PE { public Decision e(Policy p) { } }
```

**Imports**:
```java
// Organize imports:
// 1. java.* and javax.*
// 2. External libraries (org.*, com.*)
// 3. Internal packages
// 4. star imports prohibited (except static)

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import com.ironbucket.policy.PolicyEngine;
```

### JavaScript/TypeScript Code Style

**Naming**:
```typescript
// Classes/Types: PascalCase
class PolicyValidator { }
interface PolicySchema { }

// Functions: camelCase
function evaluatePolicy() { }

// Constants: UPPER_SNAKE_CASE
const MAX_POLICY_SIZE = 10_000;

// Variables: camelCase
const policyName = "read-public";
```

**Formatting**:
```typescript
// Use 2-space indentation
// Max line length: 80 characters
// Use const by default, let when needed, avoid var
// Use async/await, not .then()

// Good:
async function evaluatePolicy(policy: Policy): Promise<Decision> {
  const result = await engine.evaluate(policy);
  return result;
}

// Bad:
function evaluate(p) {
  return engine.evaluate(p).then(r => r);
}
```

### Documentation Standards

**Javadoc** (Java):
```java
/**
 * Evaluates a policy against a request context.
 *
 * @param policy     the policy to evaluate, not null
 * @param context    the request context, not null
 * @return           the evaluation decision (ALLOW/DENY)
 * @throws PolicyParseException if policy is malformed
 * @since            1.0
 */
public Decision evaluate(Policy policy, Context context) {
    // Implementation
}
```

**JSDoc** (JavaScript):
```javascript
/**
 * Parses a policy JSON string into a Policy object.
 *
 * @param {string} policyJson - The JSON policy definition
 * @returns {Policy} The parsed policy object
 * @throws {PolicyParseError} If policy JSON is invalid
 * @example
 * const policy = parsePolicy('{"effect": "ALLOW"}');
 */
function parsePolicy(policyJson) {
    // Implementation
}
```

### Commit Message Conventions

```bash
# Format: <type>(<scope>): <subject>
# Example:
git commit -m "feat(policy-engine): support regex matching in resource paths"
git commit -m "fix(s3-proxy): handle multipart upload completion"
git commit -m "docs(contributing): add code style guidelines"
git commit -m "test(audit): add test for concurrent log writes"
git commit -m "refactor(gateway): simplify routing logic"
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `test`: Adding or updating tests
- `refactor`: Code restructuring (no functional change)
- `perf`: Performance improvements
- `style`: Code style changes (formatting, missing semicolons)
- `chore`: Dependency updates, config changes

**Scopes**:
- `policy-engine`: Brazz-Nossel service
- `s3-proxy`: Buzzle-Vane service
- `audit`: Claimspindel service
- `gateway`: Sentinel-Gear service
- `docs`: Documentation files
- `testing`: Test suite

---

## 🧪 Testing Requirements

### Test Coverage Requirements

| Component | Minimum Coverage | Target |
|-----------|---|---|
| Policy Engine | 80% | 95% |
| S3 Proxy | 75% | 90% |
| Audit Logger | 85% | 95% |
| Gateway | 70% | 85% |

### Running Tests Locally

```bash
# Java tests (unit + integration)
cd Brazz-Nossel
mvn clean test

# JavaScript tests
cd ironbucket-shared-testing
npm test

# All tests with coverage
npm run test:coverage

# E2E tests
cd steel-hammer
./e2e-test-standalone.sh
```

### Test File Organization

```
src/
├── main/
│   └── java/com/ironbucket/...
└── test/
    └── java/com/ironbucket/...  # Tests mirror main structure
```

**Example test**:
```java
@SpringBootTest
class PolicyEvaluatorTest {
    
    @Autowired
    private PolicyEvaluator evaluator;
    
    @BeforeEach
    void setUp() {
        // Test setup
    }
    
    @Test
    void testAllowSimplePolicy() {
        // Arrange
        String policyJson = """
            {
              "effect": "ALLOW",
              "resources": ["bucket/public/*"]
            }
            """;
        Policy policy = PolicyParser.parse(policyJson);
        
        // Act
        Decision decision = evaluator.evaluate(policy, 
            createTestContext("bucket/public/file.txt"));
        
        // Assert
        assertEquals(Decision.ALLOW, decision);
    }
    
    @Test
    void testDenyUnmatchedResource() {
        // Arrange
        Policy policy = createRestrictivePolicy();
        
        // Act
        Decision decision = evaluator.evaluate(policy,
            createTestContext("bucket/private/file.txt"));
        
        // Assert
        assertEquals(Decision.DENY, decision);
    }
}
```

---

## 🔄 Pull Request Process

### Before Creating a PR

1. **Sync with upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run all tests**:
   ```bash
   mvn clean test
   npm test
   ```

3. **Check code quality**:
   ```bash
   # Use your IDE's linter (Checkstyle, ESLint)
   # No warnings should remain
   ```

4. **Format code**:
   ```bash
   # Java: Use IDE auto-format (Google Java Style)
   # JS: npx prettier --write .
   ```

### PR Title & Description

**Title Format**:
```
[COMPONENT] Brief description (max 50 chars)

Examples:
✅ [POLICY-ENGINE] Support regex matching in resource paths
✅ [S3-PROXY] Fix multipart upload handling
✅ [DOCS] Improve quickstart instructions
❌ Fix bug
❌ Update stuff
```

**Description Template**:
```markdown
## Description
Brief overview of changes

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing functionality to change)
- [ ] Documentation update

## Testing
Describe testing performed:
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] E2E tests pass
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No new warnings introduced
- [ ] Tests pass locally
- [ ] Changes are backwards compatible

## Related Issues
Closes #123
Related to #456

## Additional Context
Any additional information...
```

### PR Review Process

**What reviewers look for**:
- ✅ Tests included (new code needs tests)
- ✅ Style follows guidelines
- ✅ No security issues
- ✅ Clear commit history
- ✅ Documentation updated
- ✅ Performance impact considered

**Review timeline**:
- 📍 Triage: 24 hours (add to queue or request info)
- 🔍 Review: 2-3 business days (detailed feedback)
- ✅ Approval: 5-7 business days (if changes needed) or 2-3 (if approved)
- 🚀 Merge: After approval + tests passing

### Addressing Feedback

```bash
# Make requested changes
git add .
git commit -m "Address review feedback"

# DO NOT force push unless asked
# Push to your branch
git push origin feature/your-feature-name

# Reviewers will see updated commits
```

---

## 📦 Adding Dependencies

### Java Dependencies

**Only through Maven**:
```bash
# DO NOT add jar files directly

# Add to pom.xml:
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.1.0</version>
</dependency>

# Verify it's available
mvn dependency:tree
```

**Guidelines**:
- Prefer established libraries (Spring, Apache)
- Avoid duplicates (search existing dependencies first)
- Keep version numbers consistent across modules
- Document why new dependency is needed

### JavaScript Dependencies

```bash
# Install
npm install lodash --save

# Update package.json automatically
# Commit package-lock.json
git add package.json package-lock.json
git commit -m "chore: add lodash dependency"
```

**Guidelines**:
- Consider bundle size impact
- Prefer minimal, focused libraries
- Check maintenance status
- Avoid peer dependency conflicts

---

## 🐛 Debugging & Development Tips

### Local Development Setup

```bash
# Terminal 1: Start infrastructure
cd steel-hammer
docker-compose -f docker-compose-steel-hammer.yml up

# Terminal 2: Build & run a service
cd Brazz-Nossel
mvn spring-boot:run

# Terminal 3: Run tests or interact
# Make requests with curl/Postman
curl http://localhost:8081/actuator/health
```

### Viewing Logs

```bash
# Docker logs
docker logs -f brazz-nossel-service

# Kubernetes logs (production)
kubectl logs -f deployment/brazz-nossel

# Search for errors
docker logs brazz-nossel-service | grep ERROR
```

### Debugging with IDE

**IntelliJ IDEA**:
1. Open project in IntelliJ
2. Right-click service folder → Open Module Settings
3. Run → Edit Configurations
4. Create Spring Boot run configuration
5. Set breakpoints and debug

**VS Code**:
1. Install Debugger for Java extension
2. Create `.vscode/launch.json`:
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch Policy Engine",
            "request": "launch",
            "cwd": "${workspaceFolder}/Brazz-Nossel",
            "console": "integratedTerminal"
        }
    ]
}
```

### Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| Port already in use | Service running twice | `docker-compose down`, wait 30s |
| Connection refused | Service not started | Check `docker-compose ps` |
| Tests fail locally | Dependency issues | `mvn clean test` |
| Stale Docker image | Old cache | `docker-compose pull` |
| Database connection error | PostgreSQL not ready | Wait longer on startup |

---

## 📚 Additional Resources

### Documentation
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design
- [policy-schema.md](docs/policy-schema.md) - Policy specification
- [s3-proxy-contract.md](docs/s3-proxy-contract.md) - API contract
- [COMMUNITY-ROADMAP.md](COMMUNITY-ROADMAP.md) - Future plans

### Tools & References
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Maven Documentation](https://maven.apache.org/)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Jest Testing Framework](https://jestjs.io/)

### Getting Help
- 💬 GitHub Discussions (for questions)
- 🐛 GitHub Issues (for bugs)
- 💌 Community email (when established)
- 📖 Wiki (when established)

---

## 🎯 Good First Issues

Looking for a place to start?

**Beginner-friendly tasks**:
- [ ] Add missing tests (low complexity)
- [ ] Improve documentation examples (low complexity)
- [ ] Fix broken links in docs (trivial)
- [ ] Add error message improvements (low complexity)
- [ ] Refactor code for readability (low-medium complexity)

**Labels**: `good-first-issue` `documentation` `help-wanted`

---

## 🏆 Recognition

Contributors are recognized through:
- ✅ Mention in CHANGELOG.md
- ⭐ GitHub profile link in README.md
- 🏅 Contributor badge (for 5+ merged PRs)
- 💼 Potential opportunities for maintainer role

---

## 📋 Contribution Checklist

Before submitting your PR:

- [ ] Code follows style guidelines
- [ ] Tests written and passing
- [ ] No console warnings/errors
- [ ] Documentation updated
- [ ] Commit messages clear and descriptive
- [ ] Changes reviewed by you (self-review)
- [ ] PR description complete
- [ ] Related issues referenced
- [ ] Breaking changes documented
- [ ] Screenshots/demos added (if relevant)

---

## 🙏 Thank You!

Thank you for contributing to IronBucket! Your efforts help make this project better for everyone.

Have questions? Check our [FAQ](DOCS-INDEX.md#faq) or ask in GitHub Discussions.

---

**Last Updated**: January 15, 2026  
**Maintained By**: @ZiggiZagga  
**Review Cycle**: Quarterly

Status: ✅ COMPLETE & READY FOR COMMUNITY
