package model

import common.BuildCache
import common.JvmCategory
import common.JvmVendor
import common.JvmVersion
import common.Os
import common.builtInRemoteBuildCacheNode
import configurations.BuildDistributions
import configurations.CompileAll
import configurations.DependenciesCheck
import configurations.FunctionalTest
import configurations.Gradleception
import configurations.SanityCheck
import configurations.SmokeTests
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

enum class StageNames(override val stageName: String, override val description: String, override val uuid: String) : StageName {
    QUICK_FEEDBACK_LINUX_ONLY("Quick Feedback - Linux Only", "Run checks and functional tests (embedded executer, Linux)", "QuickFeedbackLinuxOnly"),
    QUICK_FEEDBACK("Quick Feedback", "Run checks and functional tests (embedded executer, Windows)", "QuickFeedback"),
    READY_FOR_MERGE("Ready for Merge", "Run performance and functional tests (against distribution)", "BranchBuildAccept"),
    READY_FOR_NIGHTLY("Ready for Nightly", "Rerun tests in different environments / 3rd party components", "MasterAccept"),
    READY_FOR_RELEASE("Ready for Release", "Once a day: Rerun tests in more environments", "ReleaseAccept"),
    HISTORICAL_PERFORMANCE("Historical Performance", "Once a week: Run performance tests for multiple Gradle versions", "HistoricalPerformance"),
    EXPERIMENTAL("Experimental", "On demand: Run experimental tests", "Experimental"),
    WINDOWS_10_EVALUATION("Experimental Windows10", "On demand checks to test Windows 10 agents", "ExperimentalWindows10"),
}

data class CIBuildModel(
    val projectPrefix: String = "Gradle_Check_",
    val rootProjectName: String = "Check",
    val tagBuilds: Boolean = true,
    val publishStatusToGitHub: Boolean = true,
    val masterAndReleaseBranches: List<String> = listOf("master", "release"),
    val parentBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val childBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val buildScanTags: List<String> = emptyList(),
    val stages: List<Stage> = listOf(
        Stage(StageNames.QUICK_FEEDBACK_LINUX_ONLY,
            specificBuilds = listOf(
                SpecificBuild.CompileAll, SpecificBuild.SanityCheck),
            functionalTests = listOf(
                TestCoverage(1, TestType.quick, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)), omitsSlowProjects = true),
        Stage(StageNames.QUICK_FEEDBACK,
            functionalTests = listOf(
                TestCoverage(2, TestType.quick, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
            functionalTestsDependOnSpecificBuilds = true,
            omitsSlowProjects = true,
            dependsOnSanityCheck = true),
        Stage(StageNames.READY_FOR_MERGE,
            specificBuilds = listOf(
                SpecificBuild.BuildDistributions,
                SpecificBuild.Gradleception,
                SpecificBuild.SmokeTestsMinJavaVersion,
                SpecificBuild.SmokeTestsMaxJavaVersion
            ),
            functionalTests = listOf(
                TestCoverage(3, TestType.platform, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(4, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(20, TestType.instant, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
            performanceTests = listOf(PerformanceTestType.test),
            omitsSlowProjects = true),
        Stage(StageNames.READY_FOR_NIGHTLY,
            trigger = Trigger.eachCommit,
            functionalTests = listOf(
                TestCoverage(5, TestType.quickFeedbackCrossVersion, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(6, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(7, TestType.parallel, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))
        ),
        Stage(StageNames.READY_FOR_RELEASE,
            trigger = Trigger.daily,
            functionalTests = listOf(
                TestCoverage(8, TestType.soak, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(9, TestType.soak, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(10, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(11, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(12, TestType.noDaemon, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(13, TestType.noDaemon, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(14, TestType.platform, Os.macos, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(15, TestType.forceRealizeDependencyManagement, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(16, TestType.allVersionsIntegMultiVersion, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(17, TestType.allVersionsIntegMultiVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
            performanceTests = listOf(
                PerformanceTestType.slow)),
        Stage(StageNames.HISTORICAL_PERFORMANCE,
            trigger = Trigger.weekly,
            performanceTests = listOf(
                PerformanceTestType.historical, PerformanceTestType.flakinessDetection, PerformanceTestType.experiment)),
        Stage(StageNames.EXPERIMENTAL,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(16, TestType.quick, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(17, TestType.quick, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(18, TestType.platform, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(19, TestType.platform, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor))),
        Stage(StageNames.WINDOWS_10_EVALUATION,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(20, TestType.quick, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)))),
    val subProjects: List<GradleSubProject> = listOf(
        GradleSubProject("antlr"),
        GradleSubProject("baseServices"),
        GradleSubProject("baseServicesGroovy", functionalTests = false),
        GradleSubProject("bootstrap", unitTests = false, functionalTests = false),
        GradleSubProject("buildCache"),
        GradleSubProject("buildCacheHttp", unitTests = false),
        GradleSubProject("buildCachePackaging", functionalTests = false),
        GradleSubProject("buildEvents"),
        GradleSubProject("buildProfile"),
        GradleSubProject("buildOption", functionalTests = false),
        GradleSubProject("buildInit"),
        GradleSubProject("cli", functionalTests = false),
        GradleSubProject("codeQuality"),
        GradleSubProject("compositeBuilds"),
        GradleSubProject("core", crossVersionTests = true),
        GradleSubProject("coreApi", functionalTests = false),
        GradleSubProject("dependencyManagement", crossVersionTests = true),
        GradleSubProject("diagnostics"),
        GradleSubProject("ear"),
        GradleSubProject("execution"),
        GradleSubProject("fileCollections"),
        GradleSubProject("files", functionalTests = false),
        GradleSubProject("hashing", functionalTests = false),
        GradleSubProject("ide", crossVersionTests = true),
        GradleSubProject("ideNative"),
        GradleSubProject("idePlay", unitTests = false),
        GradleSubProject("instantExecution"),
        GradleSubProject("instantExecutionReport", unitTests = false, functionalTests = false),
        GradleSubProject("integTest", unitTests = false, crossVersionTests = true),
        GradleSubProject("internalIntegTesting"),
        GradleSubProject("internalPerformanceTesting"),
        GradleSubProject("internalTesting", functionalTests = false),
        GradleSubProject("ivy", crossVersionTests = true),
        GradleSubProject("jacoco"),
        GradleSubProject("javascript"),
        GradleSubProject("jvmServices", functionalTests = false),
        GradleSubProject("languageGroovy"),
        GradleSubProject("languageJava", crossVersionTests = true),
        GradleSubProject("languageJvm"),
        GradleSubProject("languageNative"),
        GradleSubProject("languageScala"),
        GradleSubProject("launcher"),
        GradleSubProject("logging"),
        GradleSubProject("maven", crossVersionTests = true),
        GradleSubProject("messaging"),
        GradleSubProject("modelCore"),
        GradleSubProject("modelGroovy"),
        GradleSubProject("native"),
        GradleSubProject("persistentCache"),
        GradleSubProject("pineapple", unitTests = false, functionalTests = false),
        GradleSubProject("platformBase"),
        GradleSubProject("platformJvm"),
        GradleSubProject("platformNative"),
        GradleSubProject("platformPlay", containsSlowTests = true),
        GradleSubProject("pluginDevelopment"),
        GradleSubProject("pluginUse"),
        GradleSubProject("plugins"),
        GradleSubProject("processServices"),
        GradleSubProject("publish"),
        GradleSubProject("reporting"),
        GradleSubProject("resources"),
        GradleSubProject("resourcesGcs"),
        GradleSubProject("resourcesHttp"),
        GradleSubProject("resourcesS3"),
        GradleSubProject("resourcesSftp"),
        GradleSubProject("scala"),
        GradleSubProject("signing"),
        GradleSubProject("snapshots"),
        GradleSubProject("samples", unitTests = false, functionalTests = true),
        GradleSubProject("testKit"),
        GradleSubProject("testingBase"),
        GradleSubProject("testingJvm"),
        GradleSubProject("testingJunitPlatform", unitTests = false, functionalTests = false),
        GradleSubProject("testingNative"),
        GradleSubProject("toolingApi", crossVersionTests = true),
        GradleSubProject("toolingApiBuilders", functionalTests = false),
        GradleSubProject("toolingNative", unitTests = false, functionalTests = false, crossVersionTests = true),
        GradleSubProject("versionControl"),
        GradleSubProject("workers"),
        GradleSubProject("workerProcesses", unitTests = false, functionalTests = false),
        GradleSubProject("wrapper", crossVersionTests = true),

        GradleSubProject("soak", unitTests = false, functionalTests = false),

        GradleSubProject("apiMetadata", unitTests = false, functionalTests = false),
        GradleSubProject("kotlinDsl", unitTests = true, functionalTests = true),
        GradleSubProject("kotlinDslProviderPlugins", unitTests = true, functionalTests = false),
        GradleSubProject("kotlinDslToolingModels", unitTests = false, functionalTests = false),
        GradleSubProject("kotlinDslToolingBuilders", unitTests = true, functionalTests = true, crossVersionTests = true),
        GradleSubProject("kotlinDslPlugins", unitTests = false, functionalTests = true),
        GradleSubProject("kotlinDslTestFixtures", unitTests = true, functionalTests = false),
        GradleSubProject("kotlinDslIntegTests", unitTests = false, functionalTests = true),
        GradleSubProject("kotlinCompilerEmbeddable", unitTests = false, functionalTests = false),

        GradleSubProject("architectureTest", unitTests = false, functionalTests = false),
        GradleSubProject("distributionsDependencies", unitTests = false, functionalTests = false),
        GradleSubProject("buildScanPerformance", unitTests = false, functionalTests = false),
        GradleSubProject("distributions", unitTests = false, functionalTests = false),
        GradleSubProject("docs", unitTests = false, functionalTests = false),
        GradleSubProject("installationBeacon", unitTests = false, functionalTests = false),
        GradleSubProject("internalAndroidPerformanceTesting", unitTests = false, functionalTests = false),
        GradleSubProject("performance", unitTests = false, functionalTests = false),
        GradleSubProject("runtimeApiInfo", unitTests = false, functionalTests = false),
        GradleSubProject("smokeTest", unitTests = false, functionalTests = false)
    )
)

interface BuildTypeBucket {
    fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): FunctionalTest
}

data class GradleSubProject(val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val containsSlowTests: Boolean = false) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} ($name)",
            "${testCoverage.asName()} for $name",
            testCoverage,
            stage,
            listOf(name)
        )

    fun hasTestsOf(testType: TestType) = (unitTests && testType.unitTests) || (functionalTests && testType.functionalTests) || (crossVersionTests && testType.crossVersionTests)

    fun asDirectoryName(): String {
        return name.replace(Regex("([A-Z])")) { "-" + it.groups[1]!!.value.toLowerCase() }
    }
}

interface StageName {
    val stageName: String
    val description: String
    val uuid: String
        get() = id
    val id: String
        get() = stageName.replace(" ", "").replace("-", "")
}

data class Stage(val stageName: StageName, val specificBuilds: List<SpecificBuild> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never, val functionalTestsDependOnSpecificBuilds: Boolean = false, val runsIndependent: Boolean = false, val omitsSlowProjects: Boolean = false, val dependsOnSanityCheck: Boolean = false) {
    val id = stageName.id
}

data class TestCoverage(val uuid: Int, val testType: TestType, val os: Os, val testJvmVersion: JvmVersion, val vendor: JvmVendor = JvmVendor.oracle, val buildJvmVersion: JvmVersion = JvmVersion.java11) {
    fun asId(model: CIBuildModel): String {
        return "${model.projectPrefix}$testCoveragePrefix"
    }

    private
    val testCoveragePrefix
        get() = "${testType.name.capitalize()}_$uuid"

    fun asConfigurationId(model: CIBuildModel, subproject: String = ""): String {
        val prefix = "${testCoveragePrefix}_"
        val shortenedSubprojectName = shortenSubprojectName(model.projectPrefix, prefix + subproject)
        return model.projectPrefix + if (subproject.isNotEmpty()) shortenedSubprojectName else "${prefix}0"
    }

    private
    fun shortenSubprojectName(prefix: String, subprojectName: String): String {
        val shortenedSubprojectName = subprojectName.replace("internal", "i").replace("Testing", "T")
        if (shortenedSubprojectName.length + prefix.length <= 80) {
            return shortenedSubprojectName
        }
        return shortenedSubprojectName.replace(Regex("[aeiou]"), "")
    }

    fun asName(): String {
        return "Test Coverage - ${testType.name.capitalize()} ${testJvmVersion.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}"
    }
}

enum class TestType(val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val timeout: Int = 180) {
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    quick(true, true, true, 60),
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    platform(true, true, true),
    // Cross version tests select a small set of versions to cover when run as part of this stage
    quickFeedbackCrossVersion(false, false, true),
    // Cross version tests select all versions to cover when run as part of this stage
    allVersionsCrossVersion(false, false, true, 240),
    // run integMultiVersionTest with all version to cover
    allVersionsIntegMultiVersion(false, true, false),
    parallel(false, true, false),
    noDaemon(false, true, false, 240),
    instant(false, true, false),
    soak(false, false, false),
    forceRealizeDependencyManagement(false, true, false)
}

enum class PerformanceTestType(val taskId: String, val displayName: String, val timeout: Int, val defaultBaselines: String = "", val extraParameters: String = "", val uuid: String? = null) {
    test("PerformanceTest", "Performance Regression Test", 420, "defaults"),
    slow("SlowPerformanceTest", "Slow Performance Regression Test", 420, "defaults", uuid = "PerformanceExperimentCoordinator"),
    experiment("PerformanceExperiment", "Performance Experiment", 420, "defaults", uuid = "PerformanceExperimentOnlyCoordinator"),
    flakinessDetection("FlakinessDetection", "Performance Test Flakiness Detection", 600, "flakiness-detection-commit"),
    historical("HistoricalPerformanceTest", "Historical Performance Test", 2280, "3.5.1,4.10.3,5.6.4,last", "--checks none");

    fun asId(model: CIBuildModel): String =
        "${model.projectPrefix}Performance${name.capitalize()}Coordinator"

    fun asUuid(model: CIBuildModel): String =
        uuid?.let { model.projectPrefix + it } ?: asId(model)
}

enum class Trigger {
    never, eachCommit, daily, weekly
}

enum class SpecificBuild {
    CompileAll {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return CompileAll(model, stage)
        }
    },
    SanityCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SanityCheck(model, stage)
        }
    },
    BuildDistributions {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return BuildDistributions(model, stage)
        }
    },
    Gradleception {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return Gradleception(model, stage)
        }
    },
    SmokeTestsMinJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MIN_VERSION)
        }
    },
    SmokeTestsMaxJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MAX_VERSION)
        }
    },
    DependenciesCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return DependenciesCheck(model, stage)
        }
    };

    abstract fun create(model: CIBuildModel, stage: Stage): BuildType
}
