pipeline {
    agent { label 'ubuntu-docker' }

    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
        TZ = 'UTC'
        REGISTRY = "ghcr.io/dailarse"
        APP_VERSION = sh(script: "./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()
        IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    }

    tools {
        maven 'maven 3.9.9'
    }

    stages {
        stage('Build, Test & Verify') {
            steps {
                script {
                    echo "Running Maven build, all tests (unit & integration), and generating coverage report..."
                    sh "mvn clean verify -B"
                }
            }
        }
        stage('Build & Push OCI Images') {
            when {
                anyOf {
                    branch 'main'
                    branch pattern: ".*main", comparator: "REGEXP"
                    expression { return env.GIT_BRANCH == 'origin/main' }
                    expression { return env.GIT_BRANCH == 'main' }
                }
            }
            steps {
                script {
                    def gitHash = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()

                    withCredentials([usernamePassword(credentialsId: 'ghcr-credentials',
                            usernameVariable: 'GHCR_USER',
                            passwordVariable: 'GHCR_TOKEN')]) {

                        sh "./mvnw compile jib:build -Dimage.tag=${gitHash} -DskipTests -Djib.serialize=true"

                        sh 'echo ${GHCR_TOKEN} | docker login ghcr.io -u ${GHCR_USER} --password-stdin'

                        def frontendImage = "${env.REGISTRY}/task-tracker-frontend"

                        sh "docker build --build-arg VERSION=${env.IMAGE_TAG} \
                            -t ${frontendImage}:${env.IMAGE_TAG} \
                            -t ${frontendImage}:${env.APP_VERSION} \
                            -t ${frontendImage}:latest ./task-tracker-frontend"

                        sh "docker push ${frontendImage}:${env.IMAGE_TAG}"
                        sh "docker push ${frontendImage}:${env.APP_VERSION}"
                        sh "docker push ${frontendImage}:latest"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                echo 'Archiving JAR artifacts...'
                if (currentBuild.result == null || currentBuild.result == 'SUCCESS' || currentBuild.result == 'UNSTABLE') {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: true
                }

                echo "Publishing JUnit test results (Surefire & Failsafe)..."
                junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                junit testResults: '**/target/failsafe-reports/*.xml', allowEmptyResults: true
            }
        }
        success {
            script {
                echo 'Pipeline successful! Publishing Code Coverage report (JaCoCo)...'
                recordCoverage(
                        tools: [
                                [parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']
                        ],
                        id: 'jacocoCoverage',
                        name: 'JaCoCo Code Coverage',
                        sourceCodeRetention: 'LAST_BUILD'
                )
            }
        }
        failure {
            script {
                echo 'Pipeline failed!'
            }
        }
        cleanup {
            echo 'Final cleanup: Cleaning workspace...'
            cleanWs()
        }
    }
}