package Gradle_Check.model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import configurations.FunctionalTest
import model.BuildTypeBucket
import model.CIBuildModel
import model.GradleSubProject
import model.Stage
import model.TestCoverage
import model.TestType
import java.io.File

typealias BuildProjectToSubProjectTestClassTimes = Map<String, Map<String, List<TestClassTime>>>

interface GradleBuildBucketProvider {
    fun createFunctionalTestsFor(stage: Stage, testConfig: TestCoverage): List<FunctionalTest>

    fun createDeferredFunctionalTestsFor(stage: Stage): List<FunctionalTest>
}

class StatisticBasedGradleBuildBucketProvider(private val model: CIBuildModel, testTimeDataJson: File) : GradleBuildBucketProvider {
    private val subProjectProvider = GradleSubProjectProvider(model.subProjects)
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> = buildBuckets(testTimeDataJson, model)

    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return buckets.getValue(testCoverage).map { it.createFunctionalTestsFor(model, stage, testCoverage) }
    }

    override fun createDeferredFunctionalTestsFor(stage: Stage): List<FunctionalTest> {
        // The first stage which doesn't omit slow projects
        val deferredStage = model.stages.find { !it.omitsSlowProjects }!!
        val deferredStageIndex = model.stages.indexOfFirst { !it.omitsSlowProjects }
        return if (stage.stageName != deferredStage.stageName) {
            emptyList()
        } else {
            val stages = model.stages.subList(0, deferredStageIndex)
            val deferredTests = mutableListOf<FunctionalTest>()
            stages.forEach { eachStage ->
                eachStage.functionalTests.forEach { testConfig ->
                    deferredTests.addAll(subProjectProvider.getSlowSubProjects().map { it.createFunctionalTestsFor(model, eachStage, testConfig) })
                }
            }
            deferredTests
        }
    }

    private
    fun buildBuckets(buildClassTimeJson: File, model: CIBuildModel): Map<TestCoverage, List<BuildTypeBucket>> {
        val jsonObj = JSON.parseObject(buildClassTimeJson.readText()) as JSONObject
        val buildProjectClassTimes: BuildProjectToSubProjectTestClassTimes = jsonObj.map { buildProjectToSubProjectTestClassTime ->
            buildProjectToSubProjectTestClassTime.key to (buildProjectToSubProjectTestClassTime.value as JSONObject).map { subProjectToTestClassTime ->
                subProjectToTestClassTime.key to (subProjectToTestClassTime.value as JSONArray).map { TestClassTime(it as JSONObject) }
            }.toMap()
        }.toMap()

        val result = mutableMapOf<TestCoverage, List<BuildTypeBucket>>()
        for (stage in model.stages) {
            for (testCoverage in stage.functionalTests) {
                when (testCoverage.testType) {
                    TestType.allVersionsIntegMultiVersion -> {
                        result[testCoverage] = listOf(AllSubProjectsIntegMultiVersionTest.INSTANCE)
                    }
                    in listOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion) -> {
                        result[testCoverage] = splitBucketsByGradleVersionForBuildProject(6)
                    }
                    else -> {
                        result[testCoverage] = splitBucketsByTestClassesForBuildProject(testCoverage, stage, buildProjectClassTimes)
                    }
                }
            }
        }
        return result
    }

    // For quickFeedbackCrossVersion and allVersionsCrossVersion, the buckets are split by Gradle version
    // By default, split them into [gradle1, gradle2, gradle3, gradle4, gradle5, gradle6]
    private fun splitBucketsByGradleVersionForBuildProject(maxGradleMajorVersion: Int) = (1..maxGradleMajorVersion).map { GradleVersionXCrossVersionTestBucket(it) }

    private
    fun splitBucketsByTestClassesForBuildProject(testCoverage: TestCoverage, stage: Stage, buildProjectClassTimes: BuildProjectToSubProjectTestClassTimes): List<BuildTypeBucket> {
        val validSubProjects = subProjectProvider.getSubProjectsFor(testCoverage, stage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: Map<String, List<TestClassTime>> = buildProjectClassTimes[testCoverage.asId(model)] ?: return validSubProjects

        val subProjectTestClassTimes: List<SubProjectTestClassTime> = subProjectToClassTimes
            .entries
            .filter { "UNKNOWN" != it.key }
            .filter { subProjectProvider.getSubProjectByName(it.key) != null }
            .map { SubProjectTestClassTime(subProjectProvider.getSubProjectByName(it.key)!!, it.value) }
        val expectedBucketSize: Int = subProjectTestClassTimes.sumBy { it.totalTime } / testCoverage.expectedBucketNumber

        return split(subProjectTestClassTimes, expectedBucketSize)
    }

    private
    fun split(subProjects: List<SubProjectTestClassTime>, expectedBucketSize: Int): List<BuildTypeBucket> {
        val buckets: List<List<SubProjectTestClassTime>> = split(subProjects, SubProjectTestClassTime::totalTime, expectedBucketSize)
        val ret = mutableListOf<BuildTypeBucket>()
        var bucketNumber = 1
        buckets.forEach { subProjectsInBucket ->
            if (subProjectsInBucket.size == 1) {
                // Split large project to potential multiple buckets
                ret.addAll(subProjectsInBucket[0].split(expectedBucketSize))
            } else {
                ret.add(SmallSubProjectBucket("bucket${bucketNumber++}", subProjectsInBucket.map { it.subProject }))
            }
        }
        return ret
    }
}

class GradleSubProjectProvider(private val subProjects: List<GradleSubProject>) {
    private val nameToSubProject = subProjects.map { it.name to it }.toMap()
    fun getSubProjectsFor(testConfig: TestCoverage, stage: Stage) =
        subProjects.filterNot { it.containsSlowTests && stage.omitsSlowProjects }
            .filter { it.hasTestsOf(testConfig.testType) }
            .filterNot { testConfig.os.ignoredSubprojects.contains(it.name) }

    fun getSubProjectByName(name: String) = nameToSubProject[name]
    fun getSlowSubProjects() = subProjects.filter { it.containsSlowTests }
}

/**
 * Split a list of object into buckets with expected size.
 *
 * For example, we have a list of number [9, 1, 2, 10, 4, 5] and the expected size is 5,
 * the result buckets will be [[10], [9], [5], [4, 1], [2]]
 */
fun <T> split(list: List<T>, function: (T) -> Int, expectedBucketSize: Int): List<List<T>> {
    val originalList = ArrayList(list)
    val ret = mutableListOf<List<T>>()

    while (originalList.isNotEmpty()) {
        val largest = originalList.removeAt(0)
        val bucket = mutableListOf<T>()
        var restCapacity = expectedBucketSize - function(largest)

        bucket.add(largest)

        while (true) {
            // Find next largest object which can fit in resetCapacity
            val index = originalList.indexOfFirst { function(it) < restCapacity }
            if (index == -1 || originalList.isEmpty()) {
                break
            }

            val nextElementToAddToBucket = originalList.removeAt(index)
            restCapacity -= function(nextElementToAddToBucket)
            bucket.add(nextElementToAddToBucket)
        }

        ret.add(bucket)
    }
    return ret
}

enum class AllSubProjectsIntegMultiVersionTest : BuildTypeBucket {
    INSTANCE;

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, "all"),
            testCoverage.asName(),
            "${testCoverage.asName()} for all subProjects",
            testCoverage,
            stage,
            emptyList()
        )
}

class GradleVersionXCrossVersionTestBucket(private val gradleMajorVersion: Int) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, "gradle$gradleMajorVersion"),
            "${testCoverage.asName()} (gradle $gradleMajorVersion)",
            "${testCoverage.asName()} for gradle $gradleMajorVersion",
            testCoverage,
            stage,
            emptyList(),
            "-PtestGradleVersion=$gradleMajorVersion"
        )
}

class LargeSubProjectSplitBucket(private val subProject: GradleSubProject, private val number: Int, private val include: Boolean, val classes: List<TestClassTime>) : BuildTypeBucket by subProject {
    val name = if (number == 1) subProject.name else "${subProject.name}_$number"

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): FunctionalTest =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} ($name)",
            "${testCoverage.asName()} for projects $name",
            testCoverage,
            stage,
            listOf(subProject.name),
            "-P${if (include) "includeTestClasses" else "excludeTestClasses"}=${classes.joinToString(",") { it.testClassSimpleName }}"
        )
}

class SmallSubProjectBucket(val name: String, private val subProjects: List<GradleSubProject>) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): FunctionalTest =
        FunctionalTest(model, testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} (${subProjects.joinToString(", ") { it.name }})",
            "${testCoverage.asName()} for ${subProjects.joinToString(", ") { it.name }}",
            testCoverage,
            stage,
            subProjects.map { it.name }
        )
}

class TestClassTime(var testClass: String, var buildTimeMs: Int) {
    val testClassSimpleName: String
        get() = this.testClass.split(".").last()

    constructor(jsonObject: JSONObject) : this(jsonObject.getString("testClass"), jsonObject.getIntValue("buildTimeMs"))
}

class SubProjectTestClassTime(val subProject: GradleSubProject, val testClassTimes: List<TestClassTime>) {
    val totalTime: Int = testClassTimes.sumBy { it.buildTimeMs }

    fun split(expectedBuildTimePerBucket: Int): List<BuildTypeBucket> {
        return if (totalTime < 1.1 * expectedBuildTimePerBucket) {
            listOf(subProject)
        } else {
            val buckets: List<List<TestClassTime>> = split(testClassTimes, TestClassTime::buildTimeMs, expectedBuildTimePerBucket)
            return if (buckets.size == 1) {
                listOf(subProject)
            } else {
                buckets.mapIndexed { index: Int, classesInBucket: List<TestClassTime> ->
                    val include = index != buckets.size - 1
                    val classes = if (include) classesInBucket else buckets.subList(0, buckets.size - 1).flatten()
                    LargeSubProjectSplitBucket(subProject, index + 1, include, classes)
                }
            }
        }
    }
}
