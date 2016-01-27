# Bitbucket Server CodeGuard Hooks

CodeGuard provides hooks for Atlassian Bitbucket to ensure high-quality code.  One hook ensures a 
successful Jenkins build of unit tests before enabling a merge into configured branches.  A second 
hook automates publishing of artifacts after successful merges into or tagging of selected branches.

## Requirements
* Your Jenkins server must have the git plugin installed
* Your Jenkins server must have a user with read access to Bitbucket server.  This
   can be either a username/password combo or an SSH key.

## Setup and Usage
1. Install plugin via Atlassian Marketplace or by building from scratch (below)
2. In your Jenkins server, create a user that has read access to Bitbucket server.  This
   can be either a username/password combo or an SSH key.
3. Configure the plugin by setting the Jenkins details including base url 
   (ex. ```https://myserver/jenkins```), and the username and password the plugin can
   use to connect to Jenkins.  This user will need to have the ability to create and run 
   all jobs.   
3. For each repository, enable the CodeGuard Verify Hook and define a regular expression 
   that identifies branches that should be verified.  For example you can use:
   ```(develop|master)``` to have CodeGuard verify any pull requests aimed at the 
   develop or master branches.  The verification job will run the script 
   ```codeguard/verify.sh``` that you define in the root of your repository.
4. For each repository, enable the CodeGuard Publish Hook and define a regular expression
   that identifies branches and tags that should be published.  For example you can use:
   ```(develop|master|v(\d)+\.(\d)+\.(\d))``` to have CodeGuard publish any time
   code is merged into develop or master or any time a tag is pushed that looks v1.2.3.
   The publishing job will run the script: ```codeguard/publish.sh``` that you create
   in the root of your repository.

## Help or requesting new features
If you experience any issues or would like to request a new feature, just open a issue
on GitHub here https://github.com/capitaltg/codeguard/issues.

## Development Setup
1. `./gradlew eclipse`
2. Import project into eclipse

## Packaging and installing manually
1. In the project directory, run `./gradlew build` to generate a jar
2. In Bitbucket Server, login and go to the "manage add-ons" admin area
3. Select the option to upload a plugin
4. Configure the plugin

## Contributing
1. Fork the repository
2. Create your feature branch (`git checkout -b awesome-new-feature`)
3. Commit your changes (`git commit -am 'add awesome feature'`)
4. Push to the branch (`git push origin awesome-new-feature`)
5. Create new Pull Request

## TODO
* Add ability to rerun verification job via Bitbucket
* Ability to customize the verification and publishing script names per repository

## LICENSE
CodeGuard is released by Capital Technology Group, LLC under the Apache 2.0
License.  See the included LICENSE file for details.

_&copy; Capital Technology Group, LLC 2016_