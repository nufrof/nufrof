/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.nufrof.classpath;

import org.junit.Test;

public class GetCpTest {
    private String jarPath = "fauxproj1/build/libs/";

    @Test
    public void classpathTest() throws Exception {
        String[] args = new String[]{jarPath};
        GetCp.main(args);
    }

}
