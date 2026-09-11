# Photo-analysis acceptance flow

This flow opens the Home surface, checks the Rewards tab, returns to Home,
opens capture through Add a meal, returns to Home again, and opens capture
through the centered camera action. It uses deterministic IDs and contains no
real photo, provider, credential, or remote-analysis fixture. Its parameterized
`appId` header is separated from the command list with the required `---` YAML
front matter marker and is syntax-checked before execution. The Intel `iosX64`
run is a temporary simulator lane; Android camera evidence must use the
explicit physical-device serial.
