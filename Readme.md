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
projects to be shared across Ruby, Javascript, Java and C# via code
generation. Because of the reach this enables, CDK is poised to become
a nexus of AWS patterns and best practices accessible via familiar
tools (mvn, npm, etc.).

Clojure can tap into this ecosystem directly by consuming the Jsii
protocol and bringing infrastructure to the REPL. REPL-driven
infrastructure turns a frustrating practice with long feedback cycles
into an enjoyable experience with immediate feedback and grants the
ability for Clojure code to be deployed to AWS with minimal
configuration.

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

**Create an object from a class**

```
;; Creates a bucket based on the CDK class @aws-cdk/aws-s3.Bucket
(cdk/require ["@aws-cdk/aws-s3" s3])
(def bucket (s3/Bucket :cdk/create parent "my-bucket" {}))
```

**Get property of an object**
```
;; Gets the bucketArn property off of the bucket isntance
(:bucketArn bucket)
```

**Set property of an object**
```
;; TODO: no examples of mutable properties in CDK yet
```

**Call a method on an object**
```
;; Grants READ permission to the lambda-function object
(bucket :grantRead lambda-function)
```

**Get static property of a class**
```
(cdk/require ["@aws-cdk/aws-lambda" lambda])
;; Get the JAVA8 runtime instance
(:JAVA_8 lambda/Runtime)
```

**Set static property of an object**
```
;; TODO: no examples of mutable properties in CDK yet
```

**Call static method on class**
```
;; Refer to the src directory as an asset to be uploaded
(lambda/Code :asset "src")
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

## Getting Started

All CDK applications have a singleton root `App` construct. This root
construct contains all of the stacks, constructs and resources in a
project. This is provided via the `defapp` macro:

```
(require '[stedi.cdk :as cdk])
(cdk/defapp app
  [this]
  ;; declare resources here
  )
```

Invocation of the `defapp` macro will create an application based on
evaluating the body of `defapp`. The first argument is the `App`
construct itself and is used to wire in stacks as shown
later. Additionly, evaluation of defapp will create a `cdk.json` file
to configure the CDK CLI.

Applications are composed of stacks which contain constitute
CloudFormation-deployable units of infrastructure:

```
(require '[stedi.cdk :as cdk])

(cdk/require ["@aws-cdk/core" cdk-core]) ; [1]

(cdk/defapp app
  [this]
  (cdk-core/Stack :cdk/create this "DevStack" {}) ; [2]
  )
```

(1) `cdk/require` loads the Jsii `@aws-cdk/core` module, creates an
ephemeral namespace and binds it to `cdk-core` locally. This enables
functionality like code completion to work with CDK. (2) instantiates
a `Stack` class into an object and binds it to the `App` passed in as
the first argument with `"DevStack"` as the `id`. This example is
enough to register a deployable stack with CDK which should show up as
`DevStack` when running `cdk ls`.

Adding your first resource is as easy as using a built-in CDK
construct:

```
(require '[stedi.cdk :as cdk])

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-s3" s3])

(cdk/defextends stack cdk-core/Stack ; [1]
  :cdk/init
  (fn [this])
    (s3/Bucket :cdk/create this "MyBucket" {})) ; [2]

(cdk/defapp app
  [this]
  (stack :cdk/create this "DevStack" {}) ; [3]
  )
```

(1) shows the `defextends` macro which allows for extending the
built-in CDK constructs. In this case we override initialization so
that we can wire in a bucket construct (2) nested within the stack.
(3) Our extended class construct is instantiated the same way as
built-in constructs.

`cdk/defapp`, `cdk/require` and `cdk/defextends` form the core API.

## Special Methods and Properties

Both `CDKObject` and `CDKClass` extend `ILookup` and `IFn`. There
are special methods and properties `Stedi CDK` adds as developer
conveniences:

```
;; Get the properties of a class as data
(:cdk/definition SomeCDKClass)
(:cdk/definition some-cdk-object-instance)

;; Jump to the online documentation for a class
(SomeCDKClass :cdk/browse)
(some-cdk-object-instance :cdk/browse)
```

## Next Steps

* Check out the [example project][4] to see the minimum setup
  required to get a Lambda deployed behind API Gateway
* Check out the [CDK API Docs][5] to see what modules are available and
  how to use them

[1]: https://docs.aws.amazon.com/cdk/latest/guide/home.html
[2]: https://github.com/aws/jsii
[3]: https://docs.aws.amazon.com/cdk/api/latest/
[4]: https://github.com/StediInc/cdk-kit/tree/master/example-app
[5]: https://docs.aws.amazon.com/cdk/api/latest/docs/aws-construct-library.html
