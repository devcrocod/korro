# Mismatched IMPORT

The IMPORT below points at `samples.concepts.StringApi`, but `sharedName` is defined in
`samples.api.Access`. Korro must NOT silently pick up `Access.sharedName` just because its short
name is unique in the sample tree — that would make IMPORT a no-op whenever the bare name happens
to be unambiguous. Expected: strict-mode failure with a diagnostic pointing at the real location.

<!---IMPORT samples.concepts.StringApi-->

<!---FUN sharedName-->
<!---END-->
