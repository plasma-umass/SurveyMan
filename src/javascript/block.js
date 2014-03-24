//TODO ability to distribute questions into blocks at runtime
//TODO ability to distribute resources among questions at runtime
// I'm thinking: the idea of a Placeholder, for questions or resources, that contains some sort of code that
// tells you which things can be chosen to fill it.
var __extends = this.__extends || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    __.prototype = b.prototype;
    d.prototype = new __();
};
/// <reference path="survey.ts"/>
/// <reference path="container.ts"/>
/// <reference path="question.ts"/>
/// <reference path="option.ts"/>
/// <reference path="node_modules/jquery/jquery.d.ts" />
/// <reference path="node_modules/underscore/underscore.d.ts" />
function getResponses(blockSize) {
    var data = JSON.parse($(FORM).val()).responses;
    return blockSize ? _.rest(data, data.length - blockSize) : data;
}

var Block = (function () {
    function Block(jsonBlock) {
        jsonBlock = _.defaults(jsonBlock, { runIf: null });
        this.runIf = jsonBlock.runIf;
        this.id = jsonBlock.id;
        this.oldContents = [];
    }
    Block.prototype.tellLast = function () {
    };

    Block.prototype.run = function (nextUp) {
    };

    Block.prototype.shouldRun = function () {
        if (this.runIf) {
            var answersGiven = _.flatten(_.pluck(getResponses(), 'selected'));
            return _.contains(answersGiven, this.runIf);
        } else {
            return true;
        }
    };

    Block.prototype.advance = function () {
        if (this.shouldRun() && !_.isEmpty(this.contents)) {
            var nextUp = this.contents.shift();
            this.oldContents.push(nextUp);
            this.run(nextUp);
        } else {
            this.container.advance();
        }
    };
    return Block;
})();

// an OuterBlock can only contain Blocks (InnerBlocks or OuterBlocks)
var OuterBlock = (function (_super) {
    __extends(OuterBlock, _super);
    function OuterBlock(jsonBlock, container) {
        _super.call(this, jsonBlock);
        this.container = container;
        jsonBlock = _.defaults(jsonBlock, { exchangeable: [] });
        this.exchangeable = jsonBlock.exchangeable;
        this.contents = makeBlocks(jsonBlock.blocks, this);
        this.contents = orderBlocks(this.contents, this.exchangeable);
    }
    OuterBlock.prototype.tellLast = function () {
        _.last(this.contents).tellLast();
    };

    OuterBlock.prototype.run = function (block) {
        block.advance();
    };
    return OuterBlock;
})(Block);

// an InnerBlock can only contain Pages
var InnerBlock = (function (_super) {
    __extends(InnerBlock, _super);
    function InnerBlock(jsonBlock, container) {
        _super.call(this, jsonBlock);
        this.container = container;
        jsonBlock = _.defaults(jsonBlock, { latinSquare: false, pseudorandomize: false, training: false, criterion: null });
        this.latinSquare = jsonBlock.latinSquare;
        this.pseudorandom = jsonBlock.pseudorandomize;
        this.training = jsonBlock.training;
        this.criterion = jsonBlock.criterion;
        if (jsonBlock.groups) {
            this.contents = this.choosePages(jsonBlock.groups);
        } else {
            this.contents = this.makePages(jsonBlock.pages);
        }
        this.orderPages();
    }
    InnerBlock.prototype.run = function (page) {
        page.display();
    };

    InnerBlock.prototype.tellLast = function () {
        _.last(this.contents).isLast = true;
    };

    InnerBlock.prototype.makePages = function (jsonPages) {
        var _this = this;
        var pages = _.map(jsonPages, function (p) {
            if (p.options) {
                return new Question(p, _this);
            } else {
                return new Statement(p, _this);
            }
        });
        return pages;
    };

    InnerBlock.prototype.choosePages = function (groups) {
        var pages = this.latinSquare ? this.chooseLatinSquare(groups) : this.chooseRandom(groups);
        return this.makePages(pages);
    };

    InnerBlock.prototype.orderPages = function () {
        if (this.pseudorandom) {
            this.pseudorandomize();
        } else {
            this.contents = _.shuffle(this.contents);
        }
    };

    InnerBlock.prototype.chooseLatinSquare = function (groups) {
        var numConditions = groups[0].length;
        var lengths = _.pluck(groups, "length");
        var pages = [];
        if (_.every(lengths, function (l) {
            return l === lengths[0];
        })) {
            var version = _.random(numConditions - 1);
            for (var i = 0; i < groups.length; i++) {
                var condition = (i + version) % numConditions;
                pages.push(groups[i][condition]);
            }
        } else {
            pages = this.chooseRandom(groups); // error passing silently - should be caught in Python
        }
        return pages;
    };

    InnerBlock.prototype.chooseRandom = function (groups) {
        var pages = _.map(groups, function (g) {
            return _.sample(g);
        });
        return pages;
    };

    InnerBlock.prototype.swapInto = function (nextP, pages) {
        for (var i = 0; i < pages.length; i++) {
            var conds = _.pluck(pages.slice(i - 1, i + 2), 'condition');
            if (conds.every(function (c) {
                return c != nextP.condition;
            })) {
                var toSwap = pages[i];
                pages[i] = nextP;
                pages.push(toSwap);
            }
        }
        return pages;
    };

    InnerBlock.prototype.pseudorandomize = function () {
        var pages = [];
        var remaining = _.shuffle(this.contents);
        pages.push(remaining.shift());
        for (var i = 1; i < this.contents.length; i++) {
            var validP = _.indexOf(remaining, function (p) {
                return p.condition != pages[i - 1].condition;
            });
            if (validP > -1) {
                pages.push(remaining.splice(validP, 1)[0]);
            } else {
                var nextP = remaining.shift();
                pages = this.swapInto(nextP, pages);
            }
        }
        this.contents = pages;
    };

    // have to meet or exceed criterion to move on
    InnerBlock.prototype.shouldLoop = function () {
        // will include Answers, but their correct will be null
        var blockResponses = getResponses(this.oldContents.length);

        //flatten is how I'm dealing with nonexclusive questions, not sure the best way
        var grades = _.flatten(_.pluck(blockResponses, 'correct'));

        //correct answers separated by questions with no specified answers count as in a row
        grades = _.reject(grades, function (g) {
            return _.isNull(g);
        });
        var metric;

        // this.criterion is necessary percent correct
        if (this.criterion < 1) {
            metric = _.compact(grades).length / grades.length;
            // this.criterion is necessary number correct in a row from the end
        } else {
            var lastIncorrect = _.lastIndexOf(grades, false);
            var metric = (lastIncorrect === -1) ? grades.length : grades.length - lastIncorrect;
        }

        return metric < this.criterion;
    };

    InnerBlock.prototype.advance = function () {
        if (this.training && _.isEmpty(this.contents) && this.shouldLoop()) {
            this.contents = this.oldContents;
            this.orderPages();
        }
        _super.prototype.advance.call(this);
    };
    return InnerBlock;
})(Block);