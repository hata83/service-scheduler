/**
 * Moves the Java sources into the layered package structure.
 *
 * The script rewrites the package declaration of every moved file and replaces
 * the fully qualified names in the imports of all sources. It also moves the
 * test sources to the package of the class under test.
 *
 * Run: node tools/refactor-packages.mjs
 */
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';

const root = 'service-scheduler-server/src';
const mainRoot = join(root, 'main/java/dev/hata/scheduler');
const testRoot = join(root, 'test/java/dev/hata/scheduler');

/** The target path of every moved main source. */
const mainMoves = {
  'auth/User.java': 'auth/domain/User.java',
  'auth/UserRepository.java': 'auth/domain/UserRepository.java',
  'auth/OtpPurpose.java': 'auth/domain/OtpPurpose.java',
  'auth/AuthService.java': 'auth/application/AuthService.java',
  'auth/AuthProperties.java': 'auth/application/AuthProperties.java',
  'auth/JwtService.java': 'auth/application/JwtService.java',
  'auth/OtpChannel.java': 'auth/application/OtpChannel.java',
  'auth/OtpStorage.java': 'auth/application/OtpStorage.java',
  'auth/SessionStore.java': 'auth/application/SessionStore.java',
  'auth/ZaloStubOtpChannel.java': 'auth/adapter/ZaloStubOtpChannel.java',
  'auth/AuthController.java': 'auth/adapter/AuthController.java',
  'auth/AuthFilter.java': 'auth/adapter/AuthFilter.java',
  'auth/AuthPrincipal.java': 'auth/adapter/AuthPrincipal.java',
  'customer/CustomerProfile.java': 'customer/domain/CustomerProfile.java',
  'customer/CustomerProfileRepository.java': 'customer/domain/CustomerProfileRepository.java',
  'customer/CustomerService.java': 'customer/application/CustomerService.java',
  'dealership/Dealership.java': 'dealership/domain/Dealership.java',
  'dealership/DealershipRepository.java': 'dealership/domain/DealershipRepository.java',
  'dealership/DealershipService.java': 'dealership/application/DealershipService.java',
  'common/JsonConfig.java': 'common/config/JsonConfig.java',
  'common/RateLimitProperties.java': 'common/config/RateLimitProperties.java',
  'common/OpenApiConfig.java': 'common/config/OpenApiConfig.java',
  'common/ApiProblem.java': 'common/exception/ApiProblem.java',
  'common/AuthException.java': 'common/exception/AuthException.java',
  'common/GlobalExceptionHandler.java': 'common/exception/GlobalExceptionHandler.java',
  'common/CustomerProfileProvider.java': 'common/port/CustomerProfileProvider.java',
  'common/DealershipFeaturesProvider.java': 'common/port/DealershipFeaturesProvider.java',
  'common/IdempotencyFilter.java': 'common/web/IdempotencyFilter.java',
  'common/IdempotencyStorage.java': 'infra/redis/RedisIdempotencyStore.java',
  'common/RedisWindowCounter.java': 'infra/redis/RedisWindowCounter.java',
  'common/RequestIdFilter.java': 'infra/logging/RequestIdFilter.java',
  'common/AdminObservabilityConfig.java': 'infra/monitoring/AdminObservabilityConfig.java',
  'common/AdminWebMvcConfig.java': 'infra/monitoring/AdminWebMvcConfig.java'
};

/** The target path of every moved test source. */
const testMoves = {
  'auth/AuthControllerTest.java': 'auth/adapter/AuthControllerTest.java',
  'auth/AuthFilterTest.java': 'auth/adapter/AuthFilterTest.java',
  'auth/AuthServiceTest.java': 'auth/application/AuthServiceTest.java',
  'auth/AuthValueTypesTest.java': 'auth/application/AuthValueTypesTest.java',
  'auth/JwtServiceTest.java': 'auth/application/JwtServiceTest.java',
  'auth/OtpStorageTest.java': 'infra/redis/RedisOtpStorageTest.java',
  'auth/SessionStoreTest.java': 'infra/redis/RedisSessionStoreTest.java',
  'common/GlobalExceptionHandlerTest.java': 'common/exception/GlobalExceptionHandlerTest.java',
  'common/IdempotencyFilterTest.java': 'common/web/IdempotencyFilterTest.java',
  'common/IdempotencyStorageTest.java': 'infra/redis/RedisIdempotencyStoreTest.java',
  'common/JsonConfigTest.java': 'common/config/JsonConfigTest.java',
  'common/RateLimitPropertiesTest.java': 'common/config/RateLimitPropertiesTest.java',
  'common/RedisWindowCounterTest.java': 'infra/redis/RedisWindowCounterTest.java',
  'common/RequestIdFilterTest.java': 'infra/logging/RequestIdFilterTest.java',
  'customer/CustomerProfileTest.java': 'customer/domain/CustomerProfileTest.java',
  'customer/CustomerServiceTest.java': 'customer/application/CustomerServiceTest.java',
  'dealership/DealershipServiceTest.java': 'dealership/application/DealershipServiceTest.java'
};

/** The fully qualified name rewrites. The longest name wins. */
const renames = {
  'dev.hata.scheduler.auth.UserRepository': 'dev.hata.scheduler.auth.domain.UserRepository',
  'dev.hata.scheduler.auth.User': 'dev.hata.scheduler.auth.domain.User',
  'dev.hata.scheduler.auth.OtpPurpose': 'dev.hata.scheduler.auth.domain.OtpPurpose',
  'dev.hata.scheduler.auth.AuthService': 'dev.hata.scheduler.auth.application.AuthService',
  'dev.hata.scheduler.auth.AuthProperties': 'dev.hata.scheduler.auth.application.AuthProperties',
  'dev.hata.scheduler.auth.JwtService': 'dev.hata.scheduler.auth.application.JwtService',
  'dev.hata.scheduler.auth.OtpChannel': 'dev.hata.scheduler.auth.application.OtpChannel',
  'dev.hata.scheduler.auth.OtpStorage': 'dev.hata.scheduler.auth.application.OtpStorage',
  'dev.hata.scheduler.auth.SessionStore': 'dev.hata.scheduler.auth.application.SessionStore',
  'dev.hata.scheduler.auth.ZaloStubOtpChannel': 'dev.hata.scheduler.auth.adapter.ZaloStubOtpChannel',
  'dev.hata.scheduler.auth.AuthController': 'dev.hata.scheduler.auth.adapter.AuthController',
  'dev.hata.scheduler.auth.AuthFilter': 'dev.hata.scheduler.auth.adapter.AuthFilter',
  'dev.hata.scheduler.auth.AuthPrincipal': 'dev.hata.scheduler.auth.adapter.AuthPrincipal',
  'dev.hata.scheduler.customer.CustomerProfileRepository': 'dev.hata.scheduler.customer.domain.CustomerProfileRepository',
  'dev.hata.scheduler.customer.CustomerProfile': 'dev.hata.scheduler.customer.domain.CustomerProfile',
  'dev.hata.scheduler.customer.CustomerService': 'dev.hata.scheduler.customer.application.CustomerService',
  'dev.hata.scheduler.dealership.DealershipRepository': 'dev.hata.scheduler.dealership.domain.DealershipRepository',
  'dev.hata.scheduler.dealership.Dealership': 'dev.hata.scheduler.dealership.domain.Dealership',
  'dev.hata.scheduler.dealership.DealershipService': 'dev.hata.scheduler.dealership.application.DealershipService',
  'dev.hata.scheduler.common.JsonConfig': 'dev.hata.scheduler.common.config.JsonConfig',
  'dev.hata.scheduler.common.RateLimitProperties': 'dev.hata.scheduler.common.config.RateLimitProperties',
  'dev.hata.scheduler.common.OpenApiConfig': 'dev.hata.scheduler.common.config.OpenApiConfig',
  'dev.hata.scheduler.common.ApiProblem': 'dev.hata.scheduler.common.exception.ApiProblem',
  'dev.hata.scheduler.common.AuthException': 'dev.hata.scheduler.common.exception.AuthException',
  'dev.hata.scheduler.common.GlobalExceptionHandler': 'dev.hata.scheduler.common.exception.GlobalExceptionHandler',
  'dev.hata.scheduler.common.CustomerProfileProvider': 'dev.hata.scheduler.common.port.CustomerProfileProvider',
  'dev.hata.scheduler.common.DealershipFeaturesProvider': 'dev.hata.scheduler.common.port.DealershipFeaturesProvider',
  'dev.hata.scheduler.common.IdempotencyFilter': 'dev.hata.scheduler.common.web.IdempotencyFilter',
  'dev.hata.scheduler.common.IdempotencyStorage': 'dev.hata.scheduler.infra.redis.RedisIdempotencyStore',
  'dev.hata.scheduler.common.RedisWindowCounter': 'dev.hata.scheduler.infra.redis.RedisWindowCounter',
  'dev.hata.scheduler.common.RequestIdFilter': 'dev.hata.scheduler.infra.logging.RequestIdFilter',
  'dev.hata.scheduler.common.AdminObservabilityConfig': 'dev.hata.scheduler.infra.monitoring.AdminObservabilityConfig',
  'dev.hata.scheduler.common.AdminWebMvcConfig': 'dev.hata.scheduler.infra.monitoring.AdminWebMvcConfig'
};

const orderedRenames = Object.entries(renames).sort((a, b) => b[0].length - a[0].length);

/** Replaces the fully qualified names in one source. */
function rewrite(text) {
  let result = text;
  for (const [from, to] of orderedRenames) {
    result = result.split(from).join(to);
  }
  return result;
}

/** Sets the package declaration of a source. */
function setPackage(text, targetPackage) {
  return text.replace(/^package\s+[\w.]+;/m, `package ${targetPackage};`);
}

function packageOf(relativePath) {
  return 'dev.hata.scheduler.' + dirname(relativePath).replace(/\\/g, '/').replace(/\//g, '.');
}

let moved = 0;
for (const [from, to] of Object.entries(mainMoves)) {
  const source = join(mainRoot, from);
  if (!existsSync(source)) {
    console.log(`skip ${from} (missing)`);
    continue;
  }
  const text = rewrite(readFileSync(source, 'utf8'));
  const target = join(mainRoot, to);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, setPackage(text, packageOf(to)), 'utf8');
  rmSync(source);
  moved++;
}

for (const [from, to] of Object.entries(testMoves)) {
  const source = join(testRoot, from);
  if (!existsSync(source)) {
    console.log(`skip ${from} (missing)`);
    continue;
  }
  const text = rewrite(readFileSync(source, 'utf8'));
  const target = join(testRoot, to);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, setPackage(text, packageOf(to)), 'utf8');
  rmSync(source);
  moved++;
}

// The sources that stay in place still need the rewritten names.
for (const base of [mainRoot, testRoot]) {
  for (const name of ['PackageIndependenceTest.java', 'platform/SchemaMigrationTest.java', 'platform/AuthFixtureReset.java']) {
    const source = join(base, name);
    if (!existsSync(source)) continue;
    const text = readFileSync(source, 'utf8');
    const rewritten = rewrite(text);
    if (rewritten !== text) {
      writeFileSync(source, rewritten, 'utf8');
    }
  }
}

console.log(`${moved} files moved`);
