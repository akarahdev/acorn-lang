# Acorn
Acorn is a programming language based off C, but designed to be higher level.
C programmers should feel right at home, while keeping code memory safe and ergonomic.

To Do List:
- [x] Functions
- [x] Auto-boxing (using box`, and `unbox`)
> Values in Acorn are implicitly wrapped in a reference-counting structure.
> Use `unbox` in front of a type to remove this wrapper, e.g. `unbox i32` for a raw `i32`.
> You can call `box` on unboxed values, and `unbox` on boxed values to convert them
> between each-other easily. Almost all interactions with direct `libc` will heavily use
> these keywords.
- [ ] Proper Type System (with structural typing, and generics)
- [ ] Reference Counting (by default, values will pass by deep copying, use `&` to pass by reference)
- [ ] Variables
- [ ] Structs
- [ ] If/Then/Else
- [ ] While
- [ ] Strings
- [ ] Arrays
- [ ] Standard Library Support
- [ ] Function Pointers