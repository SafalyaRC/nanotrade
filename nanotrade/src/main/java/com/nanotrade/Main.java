package com.nanotrade;

import com.nanotrade.cli.CommandParser;
import com.nanotrade.engine.MatchingEngine;

public class Main {
    public static void main(String[] args) {
        MatchingEngine engine = new MatchingEngine();
        CommandParser parser = new CommandParser(engine);
        parser.start();
    }
}