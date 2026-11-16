plugins { java }

extra["commonRoot"] = rootDir
extra["product"] = "acqf"
apply(from = "publication/publisher.gradle.kts")
