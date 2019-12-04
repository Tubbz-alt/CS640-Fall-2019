package edu.wisc.cs.sdn.simpledns;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class CSVParser {
    static List<Subnet> parse(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
        List<Subnet> result = new ArrayList<>();
        for (String line : lines) {
            result.add(new Subnet(line));
        }
        return result;
    }
}
