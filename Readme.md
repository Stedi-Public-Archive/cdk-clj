# cdk-clj

This library is a Clojure wrapper for [AWS Cloud Development Kit (AWS CDK)][1].

This is an alpha release. We use this library internally and consider it
stable. Nonetheless, we may still make minor changes to the API.

## Purpose

From [AWS][1]:

>The AWS Cloud Development Kit (AWS CDK) is an open-source software development
>framework to define cloud infrastructure in code and provision it through AWS
>CloudFormation.
>
>It offers a high-level object-oriented abstraction to define AWS resources
>imperatively using the power of modern programming languages. Using the CDK's
>library of infrastructure constructs, you can easily encapsulate AWS best
>practices in your infrastructure definition and share it without worrying about
>boilerplate logic.

CDK is built on Amazon's [jsii][2] project, which allows TypeScript projects to
be shared across Ruby, JavaScript, Java and C# via code generation.

`cdk-clj` taps into this ecosystem directly by consuming the `jsii` protocol and
bringing infrastructure to the REPL. REPL-driven infrastructure turns a
frustrating practice with long feedback cycles into an enjoyable experience with
immediate feedback and makes it possible for Clojure code to be deployed to AWS
with minimal configuration.

## Prerequisites

`cdk-clj` requires:

1. [Clojure][clojure]
1. [Node.js][node-js]
1. [AWS CDK CLI][cdk-cli]

## Quick Start

0. Ensure you have configured appropriate [AWS Credentials][aws-creds].

1. Install `aws-cdk`:

``` shell
npm install -g aws-cdk
```

2. Create a new project directory with the following in a `deps.edn`
   file. You will also need to include the maven dependency for any
   CDK modules you are using.

``` clojure
{:paths   ["src"]
 :deps    {org.clojure/clojure {:mvn/version "1.10.1"}}
 :aliases {:dev {:extra-paths ["cdk"]
                 :extra-deps  {stedi/cdk-clj {:git/url "git@github.com:StediInc/cdk-clj.git"
                                              :sha     "5604792d04081aadbac5066a2dc0ba6031780a26"}
                               ;; Required in order to use the "@aws-cdk/aws-s3" module below
                               software.amazon.awscdk/s3 {:mvn/version "1.12.0.DEVPREVIEW"}
                               }}}}
```

3. Create a CDK infrastructure file with the path `./cdk/stedi/cdk/my_app.clj`.

``` clojure
(ns stedi.cdk.my-app
  (:require [stedi.cdk :as cdk]
            [stedi.my-app :as my-app]))

(cdk/import ["@aws-cdk/core" Stack]
            ["@aws-cdk/aws-s3" Bucket)

(defn AppStack
  [scope id props]
  (let [stack (Stack scope id props)]
    (Bucket stack "my-bucket" {:versioned true})))

(cdk/defapp app [this]
  (AppStack this "my-app-dev"))
```

4. Open up a REPL and evaluate the following form, which will create a
   `cdk.json` file in the root of the project.

``` clojure
(require 'stedi.cdk.my-app)
```

5. List your stacks to verify correct configuration:

``` shell
cdk ls
# should return `my-app-dev`
```

6. See the YAML that this deployment will produce for CloudFormation:

```
cdk synth my-app-dev
```

7. Deploy the stack to AWS:

```
cdk deploy my-app-dev
```

## Implementation Details

[jsii][2] is a protocol that allows TypeScript classes and objects to be
consumed via an RPC protocol. This protocol exposes the ability to:

- Create objects from classes with optionally overloaded methods
- Get properties from objects
- Set properties on objects
- Call methods on objects
- Get static properties on classes
- Set static properties on classes
- Call static methods on classes
- Respond to callbacks on overloaded objects

CDK exposes its functionality via this API to allow non-JavaScript programming
languages to benefit from the functionality it provides.
`cdk-clj` maps these operations into Clojure friendly equivalents. The CDK library
relies heavily on object oriented principles and `cdk-clj` does not shy away from
those concepts. Instead, it embraces them and maps them into a Clojure-friendly
interface. In doing so, it makes the [CDK documentation][3] directly mappable to
Clojure.

There are two types introduced by this library: `CDKClass` and
`CDKObject`. Together, they expose all of the functionality of the `jsii`
protocol by implementing the `clojure.lang.ILookup` and `clojure.lang.IFn`
interfaces:

**Instantiate an object from a class**

``` clojure
;; Creates a bucket based on the CDK class @aws-cdk/aws-s3.Bucket
(cdk/import ["@aws-cdk/aws-s3" Bucket])
(def bucket (Bucket parent "my-bucket" {}))
```

**Get property of an object**
``` clojure
;; Gets the bucketArn property off of the bucket instance
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

## Next Steps

* Check out the [example project][4] to see the minimum setup required to get a
  [Lambda][stedilambda] deployed behind API Gateway
* Check out the [CDK API Docs][5] to see what modules are available and how to
  use them

## Troubleshooting

### Cannot find the 'jsii-runtime' executable (JSII_RUNTIME or PATH)

[This error][jsii-404] is non-specific and is raised on any failure to launch
the runtime process, not just the missing executable named; that the causative
exception is not chained makes this harder to debug.

One possible cause is not having the [Node.js][node-js] executable (i.e.,
`node`) on the `PATH` given to the JVM. If you're using a Node version or
[virtual environment][nodeenv] manager, add the appropriate directory to the JVM
environment.

## Contributing

Contributors are welcome to submit issues, bug reports, and feature
requests. Presently, we do not accept pull requests.

## License

cdk-clj is distributed under the [Apache License, Version 2.0][apache-2].

See [LICENSE](LICENSE) for more information.

[1]: https://github.com/aws/aws-cdk#aws-cloud-development-kit-aws-cdk
[2]: https://github.com/aws/jsii
[3]: https://docs.aws.amazon.com/cdk/api/latest/
[4]: https://github.com/StediInc/cdk-clj/tree/master/example-app
[5]: https://docs.aws.amazon.com/cdk/api/latest/docs/aws-construct-library.html

[apache-2]: https://www.apache.org/licenses/LICENSE-2.0
[aws-creds]: https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html
[cdk-cli]: https://docs.aws.amazon.com/cdk/latest/guide/tools.html
[clojure]: https://clojure.org/guides/getting_started
[jsii-404]: https://github.com/aws/jsii/blob/850f42bea4218f2563d221aff28926da16692f62/packages/jsii-java-runtime/project/src/main/java/software/amazon/jsii/JsiiRuntime.java#L220
[node-js]: https://nodejs.org/en/
[nodeenv]: https://github.com/ekalinin/nodeenv
[stedilambda]: https://github.com/StediInc/lambda
