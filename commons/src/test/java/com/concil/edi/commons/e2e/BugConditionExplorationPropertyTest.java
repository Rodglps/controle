package com.concil.edi.commons.e2e;

import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Property Test
 * 
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
 * **DO NOT attempt to fix the test or the code when it fails**
 * 
 * **Property 1: Bug Condition** - E2E Tests Create Duplicate TestContainers
 * 
 * This test verifies that when docker-compose containers are running on fixed ports
 * (Oracle:1521, RabbitMQ:5672, LocalStack:4566, SFTP Origin:2222, SFTP Destination:2223),
 * the E2ETestBase creates new TestContainers instead of connecting to existing containers.
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11**
 * 
 * **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
 * 
 * The test will document counterexamples:
 * - TestContainers are created even with docker-compose running
 * - Dynamic ports are used instead of fixed ports
 * - DDL scripts are executed again, duplicating test data
 * - Environment variables are ignored
 */
public class BugConditionExplorationPropertyTest {

    /**
     * Property 1: Bug Condition - E2E Tests Create Duplicate TestContainers
     * 
     * For any test execution where docker-compose containers are running,
     * the UNFIXED E2ETestBase creates new TestContainers instead of connecting
     * to existing containers.
     * 
     * This property test generates multiple test scenarios and verifies that:
     * 1. New containers are created (duplicate containers exist)
     * 2. Dynamic ports are used instead of fixed ports
     * 3. DDL scripts are executed again (database initialization duplication)
     * 4. Environment variables are ignored
     */
    @Property(tries = 10)
    @Label("Bug Condition: E2E Tests Create Duplicate TestContainers When Docker-Compose Running")
    void bugCondition_e2eTestsCreateDuplicateTestContainers(
            @ForAll("testExecutionScenarios") TestExecutionScenario scenario) throws Exception {
        
        System.out.println("\n--- Testing Scenario: " + scenario.description + " ---");
        
        // Analyze E2ETestBase implementation to determine its behavior
        TestInfrastructureState state = analyzeE2ETestBaseImplementation();
        
        System.out.println("Analysis Results:");
        System.out.println("  - Has @Testcontainers annotation: " + state.hasTestContainersAnnotation);
        System.out.println("  - Creates new containers: " + state.createsNewContainers);
        System.out.println("  - Uses environment variables: " + state.usesEnvironmentVariables);
        System.out.println("  - Executes DDL scripts: " + state.ddlExecuted);
        System.out.println("  - Uses fixed ports: " + state.usesFixedPorts);
        
        // **BUG CONDITION ASSERTIONS**
        // These assertions encode the EXPECTED BEHAVIOR (what should happen after fix)
        // On UNFIXED code, these will FAIL, proving the bug exists
        
        // Assertion 1: Should NOT have @Testcontainers annotation (should be removed in fix)
        System.out.println("\n[Assertion 1] Checking @Testcontainers annotation...");
        assertFalse(state.hasTestContainersAnnotation,
                "EXPECTED BEHAVIOR: FileTransferE2ETest should NOT have @Testcontainers annotation. " +
                "ACTUAL (BUG): @Testcontainers annotation is present, causing automatic container creation.");
        
        // Assertion 2: Should NOT create new containers
        System.out.println("[Assertion 2] Checking container creation...");
        assertFalse(state.createsNewContainers,
                "EXPECTED BEHAVIOR: E2ETestBase should NOT create new TestContainers. " +
                "ACTUAL (BUG): E2ETestBase creates TestContainers (oracleContainer, rabbitMQContainer, etc.).");
        
        // Assertion 3: Should use environment variables
        System.out.println("[Assertion 3] Checking environment variable usage...");
        assertTrue(state.usesEnvironmentVariables,
                "EXPECTED BEHAVIOR: E2ETestBase should read configuration from environment variables. " +
                "ACTUAL (BUG): E2ETestBase does not read environment variables (DB_URL, RABBITMQ_HOST, etc.).");
        
        // Assertion 4: Should NOT execute DDL scripts
        System.out.println("[Assertion 4] Checking DDL execution...");
        assertFalse(state.ddlExecuted,
                "EXPECTED BEHAVIOR: E2ETestBase should NOT execute DDL scripts (database already initialized). " +
                "ACTUAL (BUG): E2ETestBase has initializeDatabase() method that executes DDL scripts.");
        
        // Assertion 5: Should use fixed docker-compose ports
        System.out.println("[Assertion 5] Checking port usage...");
        assertTrue(state.usesFixedPorts,
                "EXPECTED BEHAVIOR: E2ETestBase should use fixed docker-compose ports (1521, 5672, 4566, 2222, 2223). " +
                "ACTUAL (BUG): E2ETestBase uses dynamic TestContainers ports via getMappedPort().");
        
        System.out.println("--- Scenario Complete ---\n");
    }

    /**
     * Generate test execution scenarios
     */
    @Provide
    Arbitrary<TestExecutionScenario> testExecutionScenarios() {
        return Arbitraries.of(
                new TestExecutionScenario("SFTP-to-S3 Transfer", "testSftpToS3Transfer"),
                new TestExecutionScenario("SFTP-to-SFTP Transfer", "testSftpToSftpTransfer"),
                new TestExecutionScenario("File Detection", "testFileDetection"),
                new TestExecutionScenario("Database Validation", "testDatabaseValidation"),
                new TestExecutionScenario("Integrity Check", "testIntegrityCheck")
        );
    }

    /**
     * Analyze E2ETestBase implementation to determine its behavior
     * 
     * This method inspects the current E2ETestBase and FileTransferE2ETest code to determine:
     * - Whether @Testcontainers annotation is present
     * - Whether new containers are created
     * - Whether DDL scripts are executed
     * - Whether environment variables are read
     * - Whether fixed or dynamic ports are used
     */
    private TestInfrastructureState analyzeE2ETestBaseImplementation() throws Exception {
        TestInfrastructureState state = new TestInfrastructureState();
        
        // Check 1: @Testcontainers annotation on FileTransferE2ETest
        state.hasTestContainersAnnotation = checkTestContainersAnnotation();
        
        // Check 2: Container field declarations in E2ETestBase
        state.createsNewContainers = checkContainerFieldDeclarations();
        
        // Check 3: initializeDatabase() method existence
        state.ddlExecuted = checkInitializeDatabaseMethod();
        
        // Check 4: Environment variable reading in setupInfrastructure()
        state.usesEnvironmentVariables = checkEnvironmentVariableUsage();
        
        // Check 5: Port usage (fixed vs dynamic)
        state.usesFixedPorts = checkFixedPortUsage();
        
        return state;
    }
    
    /**
     * Check if container field declarations exist in E2ETestBase
     */
    private boolean checkContainerFieldDeclarations() {
        try {
            Class<?> baseClass = Class.forName("com.concil.edi.commons.e2e.E2ETestBase");
            
            // Check for TestContainers field declarations
            boolean hasOracleContainer = hasField(baseClass, "oracleContainer");
            boolean hasRabbitMQContainer = hasField(baseClass, "rabbitMQContainer");
            boolean hasLocalStackContainer = hasField(baseClass, "localStackContainer");
            boolean hasSftpOriginContainer = hasField(baseClass, "sftpOriginContainer");
            boolean hasSftpDestinationContainer = hasField(baseClass, "sftpDestinationContainer");
            
            return hasOracleContainer || hasRabbitMQContainer || hasLocalStackContainer ||
                   hasSftpOriginContainer || hasSftpDestinationContainer;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Check if initializeDatabase() method exists in E2ETestBase
     */
    private boolean checkInitializeDatabaseMethod() {
        try {
            Class<?> baseClass = Class.forName("com.concil.edi.commons.e2e.E2ETestBase");
            
            // Look for initializeDatabase method
            try {
                baseClass.getDeclaredMethod("initializeDatabase");
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Check if environment variables are read in E2ETestBase
     * This is a heuristic check - in UNFIXED code, env vars are NOT read
     * In FIXED code, env vars like DB_URL, RABBITMQ_HOST, etc. should be read
     */
    private boolean checkEnvironmentVariableUsage() {
        // In UNFIXED code, the setupInfrastructure() method uses TestContainers
        // and gets configuration from container.getJdbcUrl(), container.getHost(), etc.
        // It does NOT read System.getenv("DB_URL"), System.getenv("RABBITMQ_HOST"), etc.
        
        // For this test, we check if the code has container field declarations
        // If it does, it's using TestContainers (UNFIXED)
        // If it doesn't, it should be reading env vars (FIXED)
        
        return !checkContainerFieldDeclarations();
    }
    
    /**
     * Check if fixed ports are used vs dynamic ports
     * In UNFIXED code: uses container.getMappedPort() which returns dynamic ports
     * In FIXED code: uses fixed ports from environment variables (1521, 5672, 4566, 2222, 2223)
     */
    private boolean checkFixedPortUsage() {
        // In UNFIXED code, the code uses getMappedPort() method calls
        // In FIXED code, the code should use fixed ports from env vars
        
        // Heuristic: if containers are created, dynamic ports are used
        return !checkContainerFieldDeclarations();
    }
    
    /**
     * Check if a class has a specific field
     */
    private boolean hasField(Class<?> clazz, String fieldName) {
        try {
            clazz.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Check if @Testcontainers annotation is present on FileTransferE2ETest
     */
    private boolean checkTestContainersAnnotation() {
        try {
            Class<?> testClass = Class.forName("com.concil.edi.commons.e2e.FileTransferE2ETest");
            return testClass.isAnnotationPresent(org.testcontainers.junit.jupiter.Testcontainers.class);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Test execution scenario
     */
    static class TestExecutionScenario {
        final String description;
        final String testMethod;

        TestExecutionScenario(String description, String testMethod) {
            this.description = description;
            this.testMethod = testMethod;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Infrastructure state after analysis
     */
    static class TestInfrastructureState {
        boolean hasTestContainersAnnotation = false;
        boolean createsNewContainers = false;
        boolean usesEnvironmentVariables = false;
        boolean ddlExecuted = false;
        boolean usesFixedPorts = false;
    }
}
