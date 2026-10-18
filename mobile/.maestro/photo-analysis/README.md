# Photo-analysis acceptance flow

This flow enters capture from Home when Home is the launch surface, checks
the capture title and close action, returns to Home, checks Rewards, and returns to Home. It then
opens capture through Add a meal, closes it with the top-right close action,
and opens capture through the centered camera action before closing it with
the same close action. It uses deterministic IDs and contains no real photo, provider,
credential, or remote-analysis fixture. Its parameterized
`appId` header is separated from the command list with the required `---` YAML
front matter marker and is syntax-checked before execution. The Intel `iosX64`
run is a temporary simulator lane; Android camera evidence must use the
explicit physical-device serial.
