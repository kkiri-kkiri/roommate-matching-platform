const fs = require('node:fs');
const path = require('node:path');
const { sanitize } = require('./run.cjs');

// Minimal structured reporter: no request headers, storage, action arguments or auth bodies.
class EvidenceReporter {
  constructor() {
    this.tests = new Map();
    this.secrets = ['E2E_LOGIN_PASSWORD', 'E2E_DB_PASSWORD', 'E2E_JWT_SECRET', 'MYSQL_ROOT_PASSWORD', 'E2E_CONTROL_KEY']
      .map(key => process.env[key]).filter(Boolean);
  }
  onBegin(config, suite) {
    this.startedAt = new Date().toISOString();
    for (const test of suite.allTests()) this.tests.set(test.id, {
      id: test.id, name: test.title, file: path.relative(config.rootDir, test.location.file),
      line: test.location.line, project: test.parent.project()?.name, attempts: [],
    });
  }
  onStepEnd(test, result, step) {
    if (step.category === 'test.step') console.log(`[e2e-step] ${step.error ? 'FAIL' : 'PASS'} ${step.title}`);
  }
  onTestEnd(test, result) {
    const steps = list => list.filter(step => step.category === 'test.step').map(step => ({
      name: step.title, durationMs: step.duration, error: step.error?.message,
      steps: steps(step.steps || []),
    }));
    this.tests.get(test.id).attempts.push({ status: result.status, retry: result.retry,
      startedAt: result.startTime.toISOString(), durationMs: result.duration,
      errors: result.errors.map(error => ({ message: error.message, stack: error.stack })), steps: steps(result.steps),
    });
    if (process.env.E2E_AUS_P1 === '1' || process.env.E2E_MC_P0 === '1' || process.env.E2E_MC_P1 === '1') {
      fs.appendFileSync(path.join(process.env.E2E_EVIDENCE_DIR, 'test-attempts.jsonl'),
        JSON.stringify(sanitize(this.tests.get(test.id), this.secrets)) + '\n');
    }
  }
  onEnd(result) {
    const directory = process.env.E2E_EVIDENCE_DIR;
    const root = path.resolve(__dirname, '../../backend/build/full-service-e2e');
    if (process.env.E2E_MANAGED !== '1' || !directory || path.dirname(directory) !== root || !/^[a-f0-9]{12}$/.test(path.basename(directory))) {
      throw new Error('Structured evidence requires the owned E2E directory');
    }
    const tests = [...this.tests.values()];
    const attempts = tests.flatMap(test => test.attempts);
    const counts = { executed: attempts.length, passed: 0, failed: 0, skipped: 0, interrupted: 0, notExecuted: tests.filter(test => !test.attempts.length).length };
    for (const attempt of attempts) {
      if (attempt.status === 'passed') counts.passed++;
      else if (attempt.status === 'skipped') counts.skipped++;
      else if (attempt.status === 'interrupted') counts.interrupted++;
      else counts.failed++;
    }
    const phase = process.env.E2E_MC_P0 === '1' || process.env.E2E_MC_P1 === '1' ? process.env.E2E_MC_PHASE : null;
    if (phase && !/^[a-z0-9-]{1,50}$/.test(phase)) throw new Error('Unsafe MC phase');
    fs.writeFileSync(path.join(directory, phase ? `results-${phase}.json` : 'results.json'), JSON.stringify(sanitize({
      startedAt: this.startedAt, finishedAt: new Date().toISOString(), status: result.status, counts,
      gitSha: process.env.E2E_GIT_SHA, tests,
    }, this.secrets), null, 2));
  }
}
module.exports = EvidenceReporter;
