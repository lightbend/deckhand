// https://github.com/akka/akka-management/tree/master/bootstrap-demo/kubernetes-api

import Dependencies._
import scala.sys.process.Process
import scala.util.control.NonFatal

ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.7"

lazy val isOpenShift = {
  sys.props.get("test.openshift").isDefined
}

lazy val check = taskKey[Unit]("check")

lazy val root = (project in file("."))
  .enablePlugins(SbtReactiveAppPlugin)
  .settings(
    name := "bootstrap-kapi-demo",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
    ),
    libraryDependencies ++= Seq(
      akkaCluster,
      akkaClusterSharding,
      akkaClusterTools,
      akkaSlj4j,
      logback,
      scalaTest
    ),
    enableAkkaClusterBootstrap := true,
    akkaClusterBootstrapSystemName := "hoboken1",

    // this logic was taken from test.sh
    check := {
      val s = streams.value
      val nm = name.value
      val v = version.value
      val namespace = "reactivelibtest1"
      val kubectl = Deckhand.kubectl(s.log)
      val docker = Deckhand.docker(s.log)
      def applyRbac: Unit =
        kubectl.apply(s"""---
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
rules:
- apiGroups: [""] # "" indicates the core API group
  resources: ["pods"]
  verbs: ["get", "watch", "list"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: read-pods
subjects:
- kind: User
  name: system:serviceaccount:$namespace:default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
""")

      try {
        if (!Deckhand.isOpenShift) {
          kubectl.tryCreate(s"namespace $namespace")
          kubectl.setCurrentNamespace(namespace)
          applyRbac
          s.log.info("applying overlay / minikube")
          kubectl.apply(Deckhand.kustomize(baseDirectory.value / "overlay" / "minikube"))
        } else {
          // work around: /rp-start: line 60: /opt/docker/bin/bootstrap-kapi-demo: Permission denied
          kubectl.command(s"adm policy add-scc-to-user anyuid system:serviceaccount:$namespace:default")
          kubectl.command(s"policy add-role-to-user system:image-builder system:serviceaccount:$namespace:default")
          applyRbac
          docker.tag(s"$nm:$v docker-registry-default.centralpark.lightbend.com/$namespace/$nm:$v")
          docker.push(s"docker-registry-default.centralpark.lightbend.com/$namespace/$nm")
          s.log.info("applying overlay / openshift")
          kubectl.apply(Deckhand.kustomize(baseDirectory.value / "overlay" / "openshift"))
        }
        kubectl.waitForPods(3)
        kubectl.describe("pods")
        kubectl.checkAkkaCluster(3, _.contains(nm))
      } finally {
        kubectl.delete(s"services,pods,deployment --all --namespace $namespace")
        kubectl.waitForPods(0)
      }
    }
  )
