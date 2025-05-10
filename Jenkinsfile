pipeline {
    agent { label 'ubuntu-docker' }

    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
        TZ = 'UTC'
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