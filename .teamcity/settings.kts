import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.Swabra
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2021.1"

project {

    vcsRoot(RnDProcessesAutomationBaseServicesEmployeeServiceVcsRoot)
    vcsRoot(F1ServiceDeployment)
    vcsRoot(EmployeeServiceOctopusVcs)

    buildType(id10CompileUtAuto)
    buildType(id42DeployToOkdProductionManual)
    buildType(id2)
    buildType(id22DeployStagingToOkdQaAuto)
    buildType(Release)

    template(F1okdDeploymentNew)

    params {
        param("GRADLE_RELEASE_MANAGEMENT_PLUGIN_VERSION_ARTIFACTORY", "2.0.2")
        param("JDK_VERSION", "11")
        param("PROJECT_VERSION", "1")
        param("RELENG_SKIP", "true")
        param("GRADLE_STAGING_PLUGIN_VERSION", "%GRADLE_RELEASE_MANAGEMENT_PLUGIN_VERSION_ARTIFACTORY%")
        param("GRADLE_RELEASE_MANAGEMENT_PLUGIN_VERSION", "%GRADLE_RELEASE_MANAGEMENT_PLUGIN_VERSION_ARTIFACTORY%")
        param("COMPONENT_NAME", "employee-service")
    }
    buildTypesOrder = arrayListOf(id10CompileUtAuto, id22DeployStagingToOkdQaAuto, Release, id2, id42DeployToOkdProductionManual)
}

object id10CompileUtAuto : BuildType({
    templates(AbsoluteId("CDCompileUTGradle"))
    id("10CompileUtAuto")
    name = "[1.0] Compile & UT [AUTO]"

    params {
        param("ARTIFACT_PATH", """
            server/build/docker_logs => server_test_docker_log
            ft/build/docker_logs => ft_docker_log
        """.trimIndent())
        param("GRADLE_TASK", "build dockerBuildImage ft dockerPushImage --rerun-tasks")
        param("GRADLE_EXTRA_PARAMETERS", "-Pdocker.registry=%DOCKER_REGISTRY% -Poctopus.github.docker.registry=%OCTOPUS_GITHUB_DOCKER_REGISTRY% -Pauth-server.client-id=%AUTH_SERVER_CLIENT_ID% -Pauth-server.client-secret=%AUTH_SERVER_CLIENT_SECRET% -Pemployee-service.user=%TECHNICAL_USER% -Pemployee-service.password=%TECHNICAL_USER_PASSWORD%")
        param("OCTOPUS_GITHUB_DOCKER_REGISTRY", "%DOCKER_REGISTRY%")
        param("CURRENT_COMMIT", "")
        password("env.TECHNICAL_USER_TOKEN", "credentialsJSON:15b80e82-a92c-4f96-9be0-4e0219bdc8bc", display = ParameterDisplay.HIDDEN)
        param("env.AUTH_SERVER_URL", "https://f1-base-services-test.spb.openwaygroup.com/auth")
        param("VERSION_TAG", "")
        param("env.AUTH_SERVER_REALM", "f1-dev")
        param("AUTH_SERVER_CLIENT_ID", "test")
    }

    vcs {
        root(EmployeeServiceOctopusVcs)
    }

    steps {
        step {
            name = "Calculate build parameters"
            id = "RUNNER_4110"
            type = "OctopusCalculateBuildParameters"
        }
        gradle {
            name = "Build & Publish to Artifactory"
            id = "RUNNER_2384"
            tasks = "%GRADLE_TASK%"
            workingDir = "%WORK_DIR%"
            gradleHome = "%env.BUILD_ENV%/GRADLE/%GRADLE_VERSION%"
            gradleParams = """
                --info
                %GRADLE_STANDARD_PARAMETERS%
                %GRADLE_EXTRA_PARAMETERS%
            """.trimIndent()
            enableStacktrace = true
            jdkHome = "%env.JAVA_HOME%"
            jvmArgs = """
                -Duser.name=%teamcity.agent.jvm.user.name% -Duser.home=%teamcity.agent.jvm.user.home%
                %JAVA_OPTS%
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
        stepsOrder = arrayListOf("RUNNER_275", "RUNNER_4110", "RUNNER_2383", "RUNNER_2473", "RUNNER_880", "RUNNER_2384", "RUNNER_2987", "RUNNER_860")
    }

    features {
        dockerSupport {
            id = "DockerSupport"
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_177,PROJECT_EXT_350"
            }
        }
    }

    requirements {
        exists("env.DOCKER", "RQ_1401")
        contains("env.OS_TYPE", "CENTOS7x64", "RQ_1406")
    }
    
    disableSettings("RUNNER_2383", "RUNNER_2473", "RUNNER_2987")
})

object id2 : BuildType({
    templates(F1okdDeploymentNew)
    id("2")
    name = "[2.2] Deploy to OKD QA (Auto)"

    buildNumberPattern = "${id10CompileUtAuto.depParamRefs["BUILD_VERSION"]}"

    params {
        param("PROJECT_VERSION", "${id10CompileUtAuto.depParamRefs["PROJECT_VERSION"]}")
        param("HELM_SERVICES_SET", "--set image.name=octopusden/employee-service --set image.tag=%PROJECT_VERSION% --set componentName=%COMPONENT_NAME% --set configLabel=%DEPLOYMENT_CONFIG_LABEL% --set dockerRegistry=%DOCKER_REGISTRY% --set route.clusterDomain=%OKD_APPS_DOMAIN% --set additionalProfile=%F1_OKD_DEPLOYMENT_ADDITIONAL_PROFILE%")
    }

    dependencies {
        snapshot(Release) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
    
    disableSettings("RUNNER_2659")
})

object id22DeployStagingToOkdQaAuto : BuildType({
    templates(F1okdDeploymentNew)
    id("22DeployStagingToOkdQaAuto")
    name = "[1.1] Deploy staging to OKD QA (Auto)"

    buildNumberPattern = "${id10CompileUtAuto.depParamRefs["BUILD_VERSION"]}"

    params {
        param("PROJECT_VERSION", "${id10CompileUtAuto.depParamRefs["PROJECT_VERSION"]}")
        param("HELM_SERVICES_SET", "--set image.name=octopusden/employee-service --set image.tag=%BUILD_VERSION% --set componentName=%COMPONENT_NAME% --set configLabel=%DEPLOYMENT_CONFIG_LABEL% --set dockerRegistry=%DOCKER_REGISTRY% --set route.clusterDomain=%OKD_APPS_DOMAIN% --set additionalProfile=%F1_OKD_DEPLOYMENT_ADDITIONAL_PROFILE%")
    }

    dependencies {
        snapshot(id10CompileUtAuto) {
        }
    }
    
    disableSettings("RUNNER_2659")
})

object id42DeployToOkdProductionManual : BuildType({
    templates(F1okdDeploymentNew)
    id("42DeployToOkdProductionManual")
    name = "[4.2] Deploy to OKD production (Manual)"

    buildNumberPattern = "${id10CompileUtAuto.depParamRefs["BUILD_VERSION"]}"

    params {
        param("PROJECT_VERSION", "${id10CompileUtAuto.depParamRefs["PROJECT_VERSION"]}")
        param("DEPLOYMENT_ENVIRONMENT", "production")
        param("HELM_SERVICES_SET", "--set image.name=octopusden/employee-service --set image.tag=%PROJECT_VERSION% --set componentName=%COMPONENT_NAME% --set configLabel=%DEPLOYMENT_CONFIG_LABEL% --set dockerRegistry=%DOCKER_REGISTRY% --set route.clusterDomain=%OKD_APPS_DOMAIN% --set additionalProfile=%F1_OKD_DEPLOYMENT_ADDITIONAL_PROFILE%")
    }

    dependencies {
        snapshot(id2) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
    
    disableSettings("RUNNER_2659")
})

object Release : BuildType({
    name = "[2.0] Release"
    description = "Call an outpatient process."

    buildNumberPattern = "${id10CompileUtAuto.depParamRefs["BUILD_NUMBER_FORMAT"]}"

    params {
        param("OCTOPUS_REPOSITORY_NAME", "octopus-employee-service")
        param("env.OCTOPUS_GITHUB_TOKEN", "%OCTOPUS_GITHUB_TOKEN%")
        param("CURRENT_COMMIT", "${id10CompileUtAuto.depParamRefs["CURRENT_COMMIT"]}")
    }

    steps {
        step {
            name = "Call a github action"
            type = "OctopusCallGitHubAction"
            param("OCTOPUS_GITHUB_TOKEN", "%env.OCTOPUS_GITHUB_TOKEN%")
            param("PROJECT_VERSION", "${id10CompileUtAuto.depParamRefs["PROJECT_VERSION"]}")
        }
    }

    dependencies {
        snapshot(id22DeployStagingToOkdQaAuto) {
        }
    }
})

object F1okdDeploymentNew : Template({
    name = "F1 OKD Deployment (new)"

    params {
        param("HELM_REPO_NAME", "helm-repo")
        param("HELM_SERVICES_SET", "--set image.tag=%BUILD_VERSION% --set componentName=%COMPONENT_NAME% --set configLabel=%DEPLOYMENT_CONFIG_LABEL% --set dockerRegistry=%DOCKER_REGISTRY% --set route.clusterDomain=%OKD_APPS_DOMAIN% --set additionalProfile=%F1_OKD_DEPLOYMENT_ADDITIONAL_PROFILE%")
        param("BUILD_VERSION", "%build.number%")
        param("DEPLOYMENT_CONFIG", "%DEPLOYMENT_CONFIG_DIR%/%COMPONENT_NAME%.yml")
        param("DEPLOYMENT_DEFAULT_CONFIG", "%DEPLOYMENT_CONFIG_DIR%/default.yml")
        param("HELM_CHART_NAME", "spring-cloud")
        param("DEPLOYMENT_ENVIRONMENT", "test")
        password("OKD_SA_TOKEN", "credentialsJSON:fb5df189-1047-4599-bb57-b439561c1a82", display = ParameterDisplay.HIDDEN)
        param("DEPLOYMENT_CONFIG_LABEL", "master")
        param("HELM_REPO_URL", "https://artifactory.openwaygroup.com/artifactory/helm")
        param("HELM_RELEASE", "%COMPONENT_NAME%-%DEPLOYMENT_ENVIRONMENT%")
        param("OKD_PROJECT_NAME", "f1")
        text("DEPLOYMENT_CONFIG_DIR", "okd/deployments/%DEPLOYMENT_ENVIRONMENT%", readOnly = true, allowEmpty = false)
    }

    vcs {
        root(F1ServiceDeployment)

        cleanCheckout = true
    }

    steps {
        script {
            name = "Login to the OKD cluster"
            id = "RUNNER_3344"
            scriptContent = """
                oc login --token=%OKD_SA_TOKEN% %OKD_SERVER_URL%
                oc project %OKD_PROJECT_NAME%
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Add Artifactory Helm Repo"
            id = "RUNNER_3529"
            scriptContent = """
                helm repo add %HELM_REPO_NAME% %HELM_REPO_URL%
                helm repo update
                helm search repo
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Helm analyse code for potential errors"
            id = "RUNNER_3370"
            scriptContent = """
                helm pull %HELM_REPO_NAME%/%HELM_CHART_NAME% --untar --untardir /tmp/chart/
                helm lint /tmp/chart/%HELM_CHART_NAME% -f %DEPLOYMENT_DEFAULT_CONFIG% -f %DEPLOYMENT_CONFIG%
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Helm simulate an installation"
            id = "RUNNER_3371"
            scriptContent = """
                helm upgrade %HELM_RELEASE% %HELM_REPO_NAME%/%HELM_CHART_NAME% \
                --atomic --install -n %OKD_PROJECT_NAME% %HELM_SERVICES_SET% -f %DEPLOYMENT_DEFAULT_CONFIG%,%DEPLOYMENT_CONFIG% \
                --dry-run
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Helm component deploy"
            id = "RUNNER_3373"
            scriptContent = """
                helm upgrade %HELM_RELEASE% %HELM_REPO_NAME%/%HELM_CHART_NAME% \
                --atomic --install --timeout 15m %HELM_SERVICES_SET% -f %DEPLOYMENT_DEFAULT_CONFIG%,%DEPLOYMENT_CONFIG% \
                -n %OKD_PROJECT_NAME%
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Unlogin from OKD cluster"
            id = "RUNNER_3530"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                oc logout
                rm -rf ~/.kube/
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Delete Helm chart archive from temp dir"
            id = "RUNNER_3544"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = "rm -rf /tmp/chart/"
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        step {
            name = "Close Deployed Issues"
            id = "RUNNER_2659"
            type = "CloseDeployedIssues"
        }
    }

    features {
        swabra {
            id = "swabra"
            filesCleanup = Swabra.FilesCleanup.AFTER_BUILD
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux", "RQ_1751")
    }
})

object EmployeeServiceOctopusVcs : GitVcsRoot({
    name = "EmployeeService_Octopus_VCS"
    url = "git@github.com:octopusden/octopus-employee-service.git"
    branch = "main"
    branchSpec = "+:refs/heads/*"
    checkoutSubmodules = GitVcsRoot.CheckoutSubmodules.IGNORE
    userForTags = "tcagent <tcagent@openwaygroup.com>"
    authMethod = uploadedKey {
        userName = "git"
        uploadedKey = "gh-octopusden"
        passphrase = "credentialsJSON:7430935f-b873-4b0c-862e-fe4ffd89e06c"
    }
    param("secure:password", "")
})

object F1ServiceDeployment : GitVcsRoot({
    name = "F1 Service Deployment"
    url = "ssh://git@bitbucket.spb.openwaygroup.com/f1/service-deployment.git"
    branch = "refs/heads/master"
    authMethod = defaultPrivateKey {
    }
    param("useAlternates", "true")
})

object RnDProcessesAutomationBaseServicesEmployeeServiceVcsRoot : GitVcsRoot({
    name = "RnDProcessesAutomation_BaseServices_EmployeeService_VCS_ROOT"
    url = "ssh://git@bitbucket.spb.openwaygroup.com/f1-arc/employee-service.git"
    branch = "master"
    branchSpec = "+:refs/heads/*"
    checkoutSubmodules = GitVcsRoot.CheckoutSubmodules.IGNORE
    userForTags = "tcagent <tcagent@openwaygroup.com>"
    authMethod = defaultPrivateKey {
        userName = "git"
    }
})
