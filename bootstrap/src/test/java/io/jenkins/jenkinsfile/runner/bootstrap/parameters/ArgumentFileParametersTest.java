package io.jenkins.jenkinsfile.runner.bootstrap.parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link ArgumentFileParameters} against files in a temporary directory.
 */
public class ArgumentFileParametersTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(String content) throws IOException {
        File file = tmp.newFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void parsesSimplePairs() throws Exception {
        File file = write("A=1\nB=2\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.size(), equalTo(2));
        assertThat(params.get("A"), equalTo("1"));
        assertThat(params.get("B"), equalTo("2"));
    }

    @Test
    public void ignoresBlankLinesAndComments() throws Exception {
        File file = write("\n# comment\n   \nA=1\n\t# another comment\nB=2\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.size(), equalTo(2));
        assertThat(params.get("A"), equalTo("1"));
        assertThat(params.get("B"), equalTo("2"));
    }

    @Test
    public void splitsOnFirstEqualsOnly() throws Exception {
        File file = write("TOKEN=a=b=c\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.get("TOKEN"), equalTo("a=b=c"));
    }

    @Test
    public void keepsEmptyValue() throws Exception {
        File file = write("EMPTY=\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.get("EMPTY"), equalTo(""));
    }

    @Test
    public void keepsSpacesInValueButTrimsName() throws Exception {
        File file = write("  NAME  = value with spaces \n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.get("NAME"), equalTo(" value with spaces "));
    }

    @Test
    public void ignoresUtf8ByteOrderMark() throws Exception {
        File file = tmp.newFile();
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] rest = "A=1\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + rest.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(rest, 0, content, bom.length, rest.length);
        Files.write(file.toPath(), content);

        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.get("A"), equalTo("1"));
    }

    @Test
    public void supportsWindowsLineEndings() throws Exception {
        File file = write("A=1\r\nB= value \r\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.size(), equalTo(2));
        assertThat(params.get("A"), equalTo("1"));
        assertThat(params.get("B"), equalTo(" value "));
    }

    @Test
    public void supportsNonAsciiValues() throws Exception {
        File file = write("GREETING=你好\n");
        Map<String, String> params = ArgumentFileParameters.parse(file);
        assertThat(params.get("GREETING"), equalTo("你好"));
    }

    @Test
    public void missingFileIsAnError() {
        File missing = new File(tmp.getRoot(), "absent.properties");
        try {
            ArgumentFileParameters.parse(missing);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("does not exist"));
        }
    }

    @Test
    public void directoryIsAnError() throws Exception {
        File dir = tmp.newFolder();
        try {
            ArgumentFileParameters.parse(dir);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("is a directory"));
        }
    }

    @Test
    public void lineWithoutEqualsReportsLineNumberAndNoValue() {
        File file;
        try {
            file = write("A=1\nBROKEN\nB=2\n");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        try {
            ArgumentFileParameters.parse(file);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("Line 2"));
            assertThat(e.getMessage(), containsString("key=value"));
            // The offending line has no value; make sure the rest of the file is not echoed.
            assertThat(e.getMessage(), not(containsString("B=2")));
        }
    }

    @Test
    public void emptyNameIsAnError() throws Exception {
        File file = write("=value\n");
        try {
            ArgumentFileParameters.parse(file);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("Line 1"));
            assertThat(e.getMessage(), containsString("empty parameter name"));
        }
    }

    @Test
    public void duplicateKeyInSameFileIsAnErrorWithBothLineNumbers() throws Exception {
        File file = write("A=1\nB=2\nA=3\n");
        try {
            ArgumentFileParameters.parse(file);
            fail("Expected ParameterSourceException");
        } catch (ParameterSourceException e) {
            assertThat(e.getMessage(), containsString("Duplicate parameter 'A'"));
            assertThat(e.getMessage(), containsString("lines 1 and 3"));
            // Values must not leak into the message.
            assertTrue("Message must not contain the value", !e.getMessage().contains("A=3"));
        }
    }

    @Test
    public void nullFileMeansNoParameters() throws Exception {
        assertTrue(ArgumentFileParameters.parse(null).isEmpty());
    }
}
