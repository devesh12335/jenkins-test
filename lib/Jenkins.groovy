pipeline {
  agent any

  environment {
    PACKAGE_NAME = "com.devesh.jenkinsd"
    AAB_PATH = "build/app/outputs/bundle/release/app-release.aab"
    TRACK = "production"
    CREDENTIALS_FILE = "gplay_service_account.json"
  }

  stages {

    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/devesh12335/jenkins-test.git'
      }
    }

    stage('Flutter Clean & Get') {
      steps {
        sh '''
        which gcloud
          flutter clean
          flutter pub get
        '''
      }
    }

    stage('Build AAB') {
      steps {
        sh '''
          flutter build appbundle --release -v
        '''
      }
    }
    


   
       // Stage 4: Deploy the App Bundle to the Google Play Store using the API.
        stage('Deploy to Play Store') {
            steps {
                script {
                    // Use a temporary directory for the service account key.
                    withCredentials([file(credentialsId: 'playstore-json', variable: 'SERVICE_ACCOUNT_JSON_FILE')]) {
                        // Obtain an OAuth 2.0 access token using the service account key.
                        // This token is required to authenticate with the Play Developer API.
                       def accessToken = "ya29.a0AS3H6Nzuxzt8yZ8q0uOgk9eTp4czUtQPk51nLCSb9JizQcNVZtxhN55CbYU3oHYI2CggrmXDuk6dT5vPD4WYLewi0ceTUIuRYmHxNPXhfCzyiGZS1wyp2EGV2kcFk_sjUEG53vx4mioyoceDSYcv6D_s9EIvlHCxrCj35aUPaCgYKASQSARMSFQHGX2MiW1eNRcqbn_hnGEf02BNM8A0175"



                        // Make sure to configure API access in your Google Play Console and link it
                        // to the Google Cloud project where your service account lives.
                        // This step fetches the API URL for a new edit, which is required to publish the app.
                        def api_url_base = "https://www.googleapis.com/androidpublisher/v3/applications/${env.PACKAGE_NAME}"
                        def edits_url = "${api_url_base}/edits"
                        
                       def editIdResponse = sh(
                    script: """
                        curl -s -X POST "${edits_url}" \\
                        -H "Authorization: Bearer ${accessToken}" \\
                        -H "Content-Type: application/json" \\
                        -d "{}"
                    """,
                    returnStdout: true
                ).trim()
                // Now this will print the full JSON response, including the edit ID.
                echo "Raw API Response for Edit ID: ${editIdResponse}"
                      // Parse the edit ID from the JSON response and store it as a simple string.
                        def editId = new groovy.json.JsonSlurper().parseText(editIdResponse).id.toString()

                      // Upload the new App Bundle (.aab) to the Play Store.
                 def upload_url = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${env.PACKAGE_NAME}/edits/${editId}/bundles?ackBundleInstallationWarning=true"
                def uploadResponse = sh(
                    script: """
                        curl -s -X POST \\
                        -H "Authorization: Bearer ${accessToken}" \\
                        -H "Content-Type: application/octet-stream" \\
                        --data-binary "@${env.AAB_PATH}" \\
                        "${upload_url}"
                    """,
                    returnStdout: true
                ).trim()
                
                echo "Raw API Response for Upload: ${uploadResponse}"

                // Parse the version code from the JSON response and store it as a simple integer.
                def versionCode
                try {
                    versionCode = new groovy.json.JsonSlurper().parseText(uploadResponse).versionCode.toInteger()
                    echo "Successfully uploaded AAB with version code: ${versionCode}"
                } catch (Exception e) {
                    error "Failed to parse version code from upload response. Response: ${uploadResponse}"
                }

                        // Assign the uploaded bundle to the specified track (e.g., 'internal').
                        def track_url = "${edits_url}/${editId}/tracks/${env.TRACK}"
                        def trackBody = "{\"releases\": [{\"versionCodes\": [\"${versionCode}\"], \"status\": \"completed\"}]}"
                        
                        sh "curl -X PUT -H 'Authorization: Bearer ${accessToken}' -H 'Content-Type: application/json' --data '${trackBody}' '${track_url}'"

                        // Commit the changes to the Play Store to finalize the release.
                        def commit_url = "${edits_url}/${editId}:commit"
                        sh "curl -X POST -H 'Authorization: Bearer ${accessToken}' -H 'Content-Type: application/json' '${commit_url}'"
                    }
                }
            }
            }

  }
}
