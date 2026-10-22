// Local source preparation only. Never connects to Docker, HTTP, MySQL or SMTP.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../..');
const work = path.join(root, 'backend/build/auth-user-survey-http5');
const dest = path.join(work, 'sources');
fs.mkdirSync(dest, {recursive: true});
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
function replaceOne(s, before, after) {
  assert.equal(s.split(before).length, 2, `Source drift: ${before.slice(0, 80)}`);
  return s.replace(before, after);
}
function disable(s, start, end) {
  const a = s.indexOf(start), b = s.indexOf(end, a + start.length);
  assert(a >= 0 && b > a, `Source drift: ${start}`);
  return s.slice(0, a) + start + '\n        throw new IllegalStateException("Use AusHttp5 only; legacy execution disabled");\n    }\n' + s.slice(b);
}
const sources = [
  'backend/tools/user-mysql-multi-instance/UserMultiInstance.java',
  'backend/tools/user-mysql-multi-instance/UserMultiServer.java',
  'backend/tools/user-mysql-multi-instance/UserMultiSupport.java',
  'backend/tools/auth-mysql-concurrency/AuthMysqlConcurrency.java',
  'backend/tools/auth-mysql-concurrency/AuthMysqlMigration.java',
  'backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java',
  'backend/tools/auth-user-survey-http5/AusHttp5.java',
  'backend/tools/auth-user-survey-http5/AusHttp5Audit.java',
];
const evidence = [];
for (const source of sources) {
  const original = fs.readFileSync(path.join(root, source));
  let s = original.toString('utf8').replace(/^\uFEFF/, '');
  const name = path.basename(source);
  if (name.startsWith('UserMulti')) {
    s = s.replaceAll('127.0.0.1:3308/', '127.0.0.1:13327/')
      .replace(/\b8081\b/g, '18093').replace(/\b8082\b/g, '18094');
  }
  if (name === 'UserMultiSupport.java') {
    s = replaceOne(s, 'static final String UUID="ffc96046-c893-11f1-8230-829c8c700e9e";',
      'static final String UUID=System.getenv("AUS5_UUID");');
    s = replaceOne(s, 's.matches("kkiri_user_mi_[0-9a-f]{12}")', 's.equals("kkiriaus5http20261022")');
    s = replaceOne(s, 's.matches("kkiri_umi_[0-9a-f]{12}")', 's.equals("aus5http20261022")');
    s = s.replaceAll('"cbede7f4b30d"', '"kkiri-aus-http5-mysql-20261022"');
  }
  if (name === 'UserMultiInstance.java') {
    s = disable(s, '    public static void main(String[] args)throws Exception {', '    static String randomSecret()');
    s = disable(s, '    static String dockerRoot(String sql)throws Exception {', '    static void provision()');
    s = disable(s, '    static void provision()throws Exception {', '    static void generateSchema()');
    s = disable(s, '    static void runUserScenarios()throws Exception {', '    static void basics()');
    s = disable(s, '    static Map<String,Object> originalSnapshot()throws Exception {', '    static void cleanup()');
  }
  if (name === 'UserMultiServer.java') {
    s = replaceOne(s, 'settings.put("app.seed.mode","NONE");',
      'settings.put("app.community.image-cleanup.enabled","false");settings.put("app.community.image-directory",System.getenv("AUS5_IMAGE_DIR"));settings.put("app.seed.mode","NONE");');
  }
  // Helper JSON writer/splitter are compiled, but their legacy entry points are disabled too.
  if (name === 'AuthMysqlConcurrency.java' || name === 'AuthMysqlMigration.java') {
    s = s.replace(/public static void main\(String\[\] args\)\s*throws Exception\s*\{/,
      'public static void disabledLegacyMain(String[] args) throws Exception {');
    assert(!/public static void main\(/.test(s), `Legacy main still enabled: ${name}`);
  }
  fs.writeFileSync(path.join(dest, name), s);
  evidence.push({source, sourceSha256: hash(original), generatedSha256: hash(s)});
}
fs.writeFileSync(path.join(work, 'source-adaptations.json'), JSON.stringify(evidence, null, 2) + '\n');
console.log(`Prepared ${sources.length} sources; only AusHttp5 has an executable validation entry point.`);
