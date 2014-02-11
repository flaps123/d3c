# d3c

A ClojureScript library to interface with [d3](http://d3js.org/). Built on [strokes](https://github.com/dribnet/strokes).

## Installation

Include `d3c` in your dependencies:

## Usage

Import `d3` (proxied from strokes):

```clojure
(require '[d3c.core :refer [d3]])
```

d3c adds support for the `ILookup` interface to objects, so keywords can be used to access object attributes.

```clojure
(:svg d3) ; => equivalent to `d3.svg` in JavaScript
```

While you can use Clojure's treading macro to replicate D3's chaining syntax, a more powerful, vector-based syntax is available through d3c.

```clojure
(d3c/append! (.select d3 "#target")
             [:g {:attr {:class "group"}}
              [:text {:text "Hello"}
              [:g {}
               [:rect {:attr {:x 0 :y 0 :width 10 :height 10}
                       :style {:fill "green"}}]]]])
```

`append!` takes a selection and any number of vectors to append to the selection using its `.append` method. Vectors passed to `append!` should have a keyword for the name of the tag, a map of configuration settings, and any number of sub-vectors that will be appended to the selection returned by `.append`.

The map of configuration settings recognizes the following keys and values:

- `:attr` The value should be a map of attribute names to values. Each pair is passed to the selection's `.attr` method.
- `:attrs` Expects a function which returns a map of attribute names to values. The function is called with each datum and the resulting map is passed to `.attr`.
- `:style` Like `:attr`, but passes pairs to the selection's `.style` method.
- `:property` Like `:attr`, but passes pairs to the selection's `.property` method.
- `:text` Passes its value to the `.text` method.
- `:html` Passes its value to the `.html` method.
- `:on` Expects a map of event names to functions. Passes each key-value pair to the selection's `.on` method.
- `:datum` Passes its value to the `.datum` method.
- `:join` Expects a two-value vector of a selector string and vector of data. Creates a sub-selection with the selector, sets the data, and calls the resulting selection's `.enter` method.

d3c has its limitations, but you can always fall back to plain d3 through ClojureScript, and I'm open to suggestions.
