// Pipeline for the Bitbucket repository: every branch runs the tests with the coverage rule,
// and develop asks Coolify to rebuild the API only after they pass.
// Requires the Docker Pipeline plugin and two "Secret text" credentials in Jenkins:
//   coolify-api-webhook  Deploy Webhook URL of the API application in Coolify
//   coolify-token        Coolify API token with deploy permission
pipeline {
    agent none

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds(abortPrevious: true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Verify') {
            agent {
                docker {
                    image 'eclipse-temurin:17-jdk'
                    // Reuses downloaded dependencies between builds.
                    args '-v mte-maven-cache:/root/.m2 -u root'
                }
            }
            steps {
                // Tests run against in-memory H2; verify also enforces the JaCoCo coverage rule.
                // mvnw is stored without the executable bit, so it is run through sh.
                sh 'sh ./mvnw -B verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Deploy') {
            when { branch 'develop' }
            agent any
            steps {
                withCredentials([
                    string(credentialsId: 'coolify-api-webhook', variable: 'COOLIFY_WEBHOOK'),
                    string(credentialsId: 'coolify-token', variable: 'COOLIFY_TOKEN')
                ]) {
                    // Single quotes keep the secrets out of Groovy interpolation and the build log.
                    sh 'curl -fsS --max-time 30 -X GET "$COOLIFY_WEBHOOK" -H "Authorization: Bearer $COOLIFY_TOKEN"'
                }
            }
        }
    }
}
