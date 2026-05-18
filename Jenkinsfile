pipeline {
    agent any

    triggers {
        // Poll GitHub every 5 minutes for new commits.
        // Replace with a GitHub webhook for instant triggering (see setup guide).
        pollSCM('H/5 * * * *')
    }

    environment {
        // Shared across builds via a volume mounted into the build container.
        ANDROID_SDK_ROOT = "${JENKINS_HOME}/.android-sdk"
    }

    stages {
        stage('Build') {
            steps {
                sh '''
                    docker run --rm \
                        -v "${WORKSPACE}:/project" \
                        -v "${JENKINS_HOME}/.gradle:/root/.gradle" \
                        -v "${ANDROID_SDK_ROOT}:/opt/android-sdk" \
                        -w /project \
                        -e ANDROID_HOME=/opt/android-sdk \
                        eclipse-temurin:21-jdk-jammy \
                        bash -c "
                            set -e
                            export PATH=\\$ANDROID_HOME/cmdline-tools/latest/bin:\\$ANDROID_HOME/platform-tools:\\$PATH

                            # Install cmdline-tools on first run; reused from cache afterwards.
                            if [ ! -f \\$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager ]; then
                                apt-get update -qq && apt-get install -y -qq wget unzip
                                wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \\
                                    -O /tmp/tools.zip
                                mkdir -p \\$ANDROID_HOME/cmdline-tools
                                unzip -q /tmp/tools.zip -d \\$ANDROID_HOME/cmdline-tools
                                mv \\$ANDROID_HOME/cmdline-tools/cmdline-tools \\$ANDROID_HOME/cmdline-tools/latest
                            fi

                            yes | sdkmanager --licenses > /dev/null 2>&1
                            sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'

                            chmod +x /project/gradlew
                            /project/gradlew assembleDebug --no-daemon

                            # Allow the Jenkins user to read the outputs created by root.
                            chmod -R a+rX /project/app/build
                        "
                '''
            }
        }

        stage('Deploy') {
            steps {
                // /frontend is bind-mounted from the host (see jenkins/start).
                sh '''
                    mkdir -p /frontend/deploy
                    cp app/build/outputs/apk/debug/*.apk /frontend/deploy/
                '''
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk', fingerprint: true
        }
    }
}
