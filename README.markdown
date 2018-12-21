Deckhand
========

*sbt companion for Kubernetes and OpenShift.*

![07-27-1962_18855F Pakhuis Maandag](doc/Pakhuis_Maandag.jpg)

### Status

Incubating.

### Setup

For sbt 1.x add the following to `project/plugins.sbt`:

```scala
addSbtPlugin("com.lightbend.rp" % "sbt-deckhand" % "X.Y.Z")
```

See [releases](https://github.com/lightbend/deckhand/releases) for the latest release.

Usage
-----

sbt-deckhand introduces `Deckhand` object to your `build.sbt`, which is intended to drive an OpenShift cluster during integration testing.

### Deckhand.kubectl

Example:

```scala
lazy val check = taskKey[Unit]("check")

check := {
  val s = streams.value
  val namespace = "somenamespace"
  val kubectl = Deckhand.kubectl(s.log)
  val yamlDir = baseDirectory.value / "kubernetes"

  try {
    kubectl.tryCreate(s"namespace $namespace")
    kubectl.setCurrentNamespace(namespace)
    kubectl.apply(yamlDir / "akka-cluster.yaml")
    kubectl.waitForPods(3)
    kubectl.describe("pods")

    // do something here...
  } finally {
    kubectl.delete(s"services,pods,deployment --all --namespace $namespace")
    kubectl.waitForPods(0)
  }
}
```

`Deckhand.kubectl(s.log)` returns `Kubectrl` value, which is a thin wrapper around `kubectl` and `oc` command line.
It provides methods such as `apply`, `get`, and `logs` each corresponding to `kubectl apply`, `kubectl get`, and `kubectl logs`.

See [Kubectl.scala](src/main/scala/com/lightbend/rp/sbtdeckhand/Kubectl.scala) for details.

#### waitForPods

`waitForPods` is a utility method to wait for the given number of pods.

```scala
kubectl.waitForPods(3)
```

Optionally you can also pass in String arguments to `kubectl get` to narrow down the pods to wait for:

```scala
kubectl.waitForPods(3, "--selector app=bootstrap-kapi-demo")
```

#### checkAkkaCluster

`checkAkkaCluster` is a utility method to wait for an Akka Cluster to bootstrap itself with the given number of members.

```scala
kubectl.checkAkkaCluster(3)
```

#### Deckhand.mustache

For simple templating, Deckhand provides [Mustache](https://mustache.github.io/) template support, which can be used to substitute variables in the YAML files.

For example given `rbac.mustache`, I'd like to plugin `{{namespace}}` in `build.sbt`.

```yaml
---
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
  name: system:serviceaccount:{{namespace}}:default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

We can call:

```scala
val kubectl = Deckhand.kubectl(s.log)
val yamlDir = baseDirectory.value / "kubernetes"
val namespace = "somenamespace"
kubectl.apply(Deckhand.mustache(yamlDir / "rbac.mustache"),
  Map(
    "namespace"       -> namespace
  ))
```

#### Deckhand.kustomize

Alternatively, if you want to overlay specific YAML patch on top of base YAML files, Deckhand provides [Kustomize](https://github.com/kubernetes-sigs/kustomize) integration.

For example here's a patch to patch the `imagePullPolicy` to `Never` for Minikube:

```yaml
apiVersion: "apps/v1beta2"
kind: Deployment
metadata:
  name: "bootstrap-kapi-demo-v0-1-0"
spec:
  template:
    spec:
      containers:
        - name: "bootstrap-kapi-demo"
          image: "bootstrap-kapi-demo:0.1.0"
          imagePullPolicy: "Never"
```

In `build.sbt` we can apply the variant YAML file as follows:

```scala
val kubectl = Deckhand.kubectl(s.log)
kubectl.apply(Deckhand.kustomize(baseDirectory.value / "overlay" / "minikube"))
```

### Deckhand.docker

Example:

```scala
lazy val check = taskKey[Unit]("check")

check := {
  val s = streams.value
  val nm = name.value
  val v = version.value
  val namespace = "somenamespace"
  val docker = Deckhand.docker(s.log)
  docker.tag(s"$nm:$v registry.yourcompany.com/$namespace/$nm:$v")
  docker.push(s"registry.yourcompany.com/$namespace/$nm")
}
```

### Photo credit

[Ben van Meerendonk / AHF, collectie IISG, Amsterdam](https://www.flickr.com/photos/iisg/6526598519/in/photostream/)
