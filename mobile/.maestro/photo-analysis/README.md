# Photo-analysis acceptance flow

This flow uses deterministic capture-surface IDs and contains no real photo,
provider, credential, or remote-analysis fixture. Its parameterized `appId`
header is separated from the command list with the required `---` YAML front
matter marker and is syntax-checked before execution. The Intel `iosX64` run
is a temporary simulator lane; Android camera evidence must use the explicit
physical-device serial.
