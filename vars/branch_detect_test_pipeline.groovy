def call() {
  node("ANDREY_A") {
    stage('PreBuild') {
      echo "Prebuld"
    }
    stage('Build') {
      echo "Build"
    }
    stage('Deploy') {
      echo "Deploy"
    }
  }
}
