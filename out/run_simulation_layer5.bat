@echo off

cd /d C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\simulation
call C:\Users\LQP\.m2\wrapper\dists\apache-maven-3.9.8-bin\4fnp1ql35bgk2a0p7m31ovbgb0\apache-maven-3.9.8\bin\mvn.cmd exec:java -Dexec.mainClass=org.matsim.project.RunMatsimBaseline -Dexec.classpathScope=compile > C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\simulation_layer5.log 2>&1
exit /b %ERRORLEVEL%