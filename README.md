Checking 1 repo: cd pr-analyzer-maven
mvn clean package
java -jar target/pr-analyzer-maven-1.0.0.jar --latest https://github.com/fedify-dev/fedify 100 report.csv   


Checking multiple repo: 
in repos.txt add repos,
java -jar target/pr-analyzer-maven-1.0.0.jar --repos repos.txt 100 report.csv  
