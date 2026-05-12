# Unknown directive-like lines

<!---IMPORT samples-->

The following directive-shaped lines are NOT Korro directives and must pass through:

<!---TODO-->
<!---TOC -->
<!---FIXME refactor later-->
<!--- this is a generic comment -->
<!---NOTE not a korro directive-->

A three-dash close also passes through because the inner content is not a valid name:

<!---TODO--->

An unclosed opener — even one that names a real directive — must NOT throw; it stays as plain text:

<!---FUN missing close marker

A real directive still expands normally:

<!---FUN example-->
<!---END-->
