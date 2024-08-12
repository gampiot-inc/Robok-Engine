package org.gampiot.robok.ui.fragments.project.create.util;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gampiot.robok.feature.template.code.JavaClassTemplate;
import org.gampiot.robok.feature.template.code.android.game.logic.GameScreenLogicTemplate;
import org.gampiot.robok.ui.fragments.project.template.model.ProjectTemplate;

public class ProjectCreator {

    public static void create(Context context, String projectName, String packageName, ProjectTemplate template) {
        try {
            InputStream zipFileInputStream = context.getAssets().open(template.zipFileName);
            File outputDir = new File(Environment.getExternalStorageDirectory(), "Robok/.projects/" + projectName);

            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(zipFileInputStream));
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName();
                    String outputFileName = entryName
                            .replace(template.name, projectName)
                            .replace("game/logic/$pkgName", "game/logic/" + packageName.replace('.', '/'));
                    
                    File outputFile = new File(outputDir, outputFileName);

                    if (!outputFile.getParentFile().exists()) {
                        outputFile.getParentFile().mkdirs();
                    }

                    FileOutputStream fos = new FileOutputStream(outputFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = zipInputStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.close();
                }
                zipInputStream.closeEntry();
            }

            zipInputStream.close();
            
            createJavaClass(outputDir, projectName, packageName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createJavaClass(File outputDir, String projectName, String packageName) {
        try {
            GameScreenLogicTemplate template = new GameScreenLogicTemplate();
            template.setName("MainScreen");
            template.setPackageName(packageName);
            
            String classFilePath = "game/logic/" + packageName.replace('.', '/') + "/" + template.getName() + ".java";
            File javaFile = new File(outputDir, classFilePath);

            if (!javaFile.getParentFile().exists()) {
                javaFile.getParentFile().mkdirs();
            }

            FileOutputStream fos = new FileOutputStream(javaFile);
            fos.write(template.getContent().getBytes());
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}