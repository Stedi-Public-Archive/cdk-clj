build :
	rm -fr target
	mkdir -p target
	mkdir -p classes
	clj -e "(compile,'stedi.lambda)"
	clj -A:pack -m mach.pack.alpha.aws-lambda target/app.zip
