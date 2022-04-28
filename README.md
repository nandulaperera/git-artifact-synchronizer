# WSO2 API Manager Artifact Synchronization with Git

Git Artifact Synchronizer is an implementation of WSO2 API Manager Gateway Artifact Synchronizer to store API artifacts in a Git repository.

Artifact Synchronization enables synchronization of runtime artifacts in an [Active-Active Deployment](https://apim.docs.wso2.com/en/latest/install-and-setup/setup/single-node/configuring-an-active-active-deployment/) of WSO2 API Manager.

> Git Artifact Synchronizer is supported by WSO2 API Manager 4.1.0 and above.

# Getting Started

- You need to create a new Git repository in a source control system. (Eg: [GitHub](https://github.com/), [GitLab](https://gitlab.com/), [Bitbucket](https://bitbucket.org/))
- Initialize the repository. (Eg: Add a `README` file)
- Generate a personal access token.
  - [Create a personal access token in GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)
  - [Create a personal access token in Bitbucket](https://confluence.atlassian.com/bitbucketserver/personal-access-tokens-939515499.html)
  - [Create a personal access token in GitLab](https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html)
- Build the project by executing the following Maven command from the root directory of the project.

> The pre-built JAR file can also be downloaded from [Releases](https://github.com/nandulaperera/git-artifact-synchronizer/releases)

  ```sh
  mvn clean install
  ```
- Once the build is successful, copy the JAR file `git-1.0.0.jar` to `<API-M_HOME>/repository/components/dropins` directory.
- Start the API Manager instance(s) with the following System properties
  - `-DGitRepoURL` - The URL of the Git repository (Eg: `https://github.com/<git-username>/<repository-name>`)
  - `-DGitUsername` - The Git username
  - `-DGitAccessToken` - The Personal Access Token generated above.
  - `-DGitRepoBranch` - The branch used to store the artifacts (Eg: `main`) (optional)

    > If the branch is kept empty, the default branch of the repository will be used to store the artifacts.


Navigate to the `<API-M_HOME>/bin` directory and execute the following command to start the API Manager instance.

- For Windows


  ```sh
  api-manager.bat --run -DGitRepoURL="https://github.com/<git-username>/<repository-name>" -DGitUsername="<git-username>" -DGitAccessToken="<git-access-token>" -DGitRepoBranch="<branch-name>"
  ```

- For Linux

  ```sh
  sh api-manager.sh -DGitRepoURL="https://github.com/<git-username>/<repository-name>" -DGitUsername="<git-username>" -DGitAccessToken="<git-access-token>" -DGitRepoBranch="<branch-name>"
  ```
- The [Artifact Directory Structure](#artifact-directory-structure) section explains how the directory structure is created when API revisions and deployments are made.

### Artifact Directory Structure

When adding, removing, deploying or undeploying the directory structure will be as follows.

- When an API revision is added/deleted, the ZIP artifact will be added/deleted to/from the `inactive/<api_name-version>` directory in the repository.
  (Eg: `inactive/PizzaShackAPI-1.0.0`)

- When an API revision is deployed/undeployed, the ZIP artifact will be added/removed to/from the `active-<gateway_name>` directory.
  (Eg: `active/Default`)

<b>Sample Directory Structure</b>
 ```text
  active-Gateway1
  ├── 1a2b3c4d-SwaggerPetstore-1.0.0-abcd1234-carbon.super.zip
  ├── 2b3c4d5e-SwaggerPetstore-2.0.0-pqrs1234-carbon.super.zip
  active-Gateway2
  ├── 2b3c4d5e-SwaggerPetstore-2.0.0-wxyz3456-carbon.super.zip
  inactive
  ├── SwaggerPetstore-1.0.0
  │   └── 1a2b3c4d-SwaggerPetstore-1.0.0-abcd1234-carbon.super.zip
  │   └── 1a2b3c4d-SwaggerPetstore-1.0.0-cdef6789-carbon.super.zip
  ├── SwaggerPetstore-2.0.0
      └── 2b3c4d5e-SwaggerPetstore-2.0.0-wxyz3456-carbon.super.zip
  
 ```
