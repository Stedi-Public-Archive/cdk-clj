# Stedi CDK

## Purpose

[CDK][1] is an AWS project that provides a wrapper for CloudFormation
that allows infrastructure to be expressed in code rather than the
provided YAML/JSON DSL. In addition to providing an interface to
writing CloudFormation in code, CDK adds a higher-level layer of
constructs that compose these primitives into reusable patterns that
bake in best practices. These constructs can be extended to create
even higher-level reusable patterns to share common infrastructure
across applications.

CDK is built on the Amazon [Jsii][2] project which allows TypeScript
projects to be shared across Ruby, JavaScript, Java and C# via code
generation. Because of the reach this enables, CDK is poised to become
a nexus of AWS patterns and best practices accessible via familiar
tools (mvn, npm, etc.).

Clojure can tap into this ecosystem directly by consuming the Jsii
protocol and bringing infrastructure to the REPL. REPL-driven
infrastructure turns a frustrating practice with long feedback cycles
into an enjoyable experience with immediate feedback and grants the
ability for Clojure code to be deployed to AWS with minimal
configuration.

## Quick Start

1. Install `aws-cdk`:

``` clojure
npm install -g aws-cdk
```

2. Create a new directory with the following `deps.edn`:

``` clojure
{:paths   ["src"]
 :deps    {org.clojure/clojure {:mvn/version "1.10.1"}}
 :aliases {:dev {:extra-paths ["cdk"]
                 :extra-deps  {stedi/cdk-kit {:git/url "git@github.com:StediInc/cdk-kit.git"
                                              :sha     "5604792d04081aadbac5066a2dc0ba6031780a26"}}}}}
```

3. Create `./cdk/stedi/cdk/my_app.clj`:

``` clojure
(ns stedi.cdk.my-app
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.my-app :as my-app]))

(cdk/import ["@aws-cdk/core" Stack])

(defn AppStack
  [scope id props]
  (let [stack (Stack scope id props)]
    (lambda/Clj stack "my-fn" {:fn #'my-app/echo})))

(cdk/defapp app [this]
  (AppStack this "my-app-dev"))
```

4. Open up a repl and `(require 'stedi.cdk.my-app)` this should create
   a `cdk.json` file in the root of the project.

5. Create `./src/stedi/my_app.clj`:

``` clojure
(ns stedi.my-app)

(defn echo [input]
  {:echoed input})
```

6. List your stacks to verify correct configuration:

```
cdk ls
# should return `my-app-dev`
```

7. See the yaml this deployment will produce for cloudformation:

```
cdk synth my-app-dev
```

8. Deploy the stack to AWS:

```
cdk deploy my-app-dev
```

## Approach

Jsii is a protocol that allows TypeScript classes and objects to be
consumed via an RPC protocol. This protocol exposes the ability to:

- Create objects from classes with optionally overloaded methods
- Get properties from objects
- Set properties on objects
- Call methods on objects
- Get static properties on classes
- Set static properties on classes
- Call static methods on classes
- Respond to callbacks on overloaded objects

CDK exposes its functionality via this API to allow non-javascript
programming languages to benefit from the functionality it provides.

`Stedi CDK` maps these operations into Clojure friendly
equivalents. The CDK library relies heavily on object oriented
principles and `Stedi CDK` does not shy away from those
concepts. Instead, it embraces them and maps them into a
Clojure-friendly interface. In doing so, it makes the [CDK
documentation][3] directly mappable to Clojure.

There are two types introduced by this library `CDKClass` and
`CDKObject`. Together, they expose all of the functionality of the
Jsii protocol by implementing the `clojure.lang.ILookup` and
`clojure.lang.IFn` interfaces:

**Instantiate an object from a class**

``` clojure
;; Creates a bucket based on the CDK class @aws-cdk/aws-s3.Bucket
(cdk/import ["@aws-cdk/aws-s3" Bucket])
(def bucket (Bucket parent "my-bucket" {}))
```

**Get property of an object**
``` clojure
;; Gets the bucketArn property off of the bucket isntance
(:bucketArn bucket)
```

**Set property of an object**
``` clojure
;; TODO: not implemented yet
```

**Call a method on an object**
``` clojure
;; Grants READ permission to the lambda-function object
(cdk/import ["@aws-cdk/aws-s3" Bucket])
(Bucket/grantRead bucket lambda-function)
```

**Get static property of a class**
``` clojure
(cdk/import ["@aws-cdk/aws-lambda" Runtime])
;; Get the JAVA8 runtime instance
(:JAVA_8 Runtime)
```

**Set static property of an object**
``` clojure
;; TODO: not implemented yet
```

**Call static method on class**
``` clojure
(cdk/import ["@aws-cdk/aws-lambda" Code])
;; Refer to the src directory as an asset to be uploaded
(Code/asset "src")
```

## Prerequisites

**CDK CLI**

```
npm install -g aws-cdk
```

**Latest clojure.tools.deps**

```
brew update clojure
```

## Next Steps

* Check out the [example project][4] to see the minimum setup
  required to get a Lambda deployed behind API Gateway
* Check out the [CDK API Docs][5] to see what modules are available and
  how to use them

## Troubleshooting

### Cannot find the 'jsii-runtime' executable (JSII_RUNTIME or PATH)
[This error][jsii-404] is non-specific and is raised on any failure to launch
the runtime process, not just the missing executable named; that the causative
exception is not chained makes this harder to debug.

One possible cause is not having the **Node.js** executable (`node`) on the PATH
given to the JVM.  If you're using a Node version or [virtual
environment][nodeenv] manager, add the appropriate directory to the JVM
environment.

## License

cdk-kit is distributed under the [Apache License, Version 2.0][apache-2].

See [LICENSE](LICENSE) for more information.


[1]: https://docs.aws.amazon.com/cdk/latest/guide/home.html
[2]: https://github.com/aws/jsii
[3]: https://docs.aws.amazon.com/cdk/api/latest/
[4]: https://github.com/StediInc/cdk-kit/tree/master/example-app
[5]: https://docs.aws.amazon.com/cdk/api/latest/docs/aws-construct-library.html

[jsii-404]: https://github.com/aws/jsii/blob/850f42bea4218f2563d221aff28926da16692f62/packages/jsii-java-runtime/project/src/main/java/software/amazon/jsii/JsiiRuntime.java#L220
[nodeenv]: https://github.com/ekalinin/nodeenv
[apache-2]: https://www.apache.org/licenses/LICENSE-2.0
