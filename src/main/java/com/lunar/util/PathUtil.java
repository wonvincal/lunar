package com.lunar.util;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;

public class PathUtil {
	private static Logger LOG = LogManager.getLogger(PathUtil.class);
	
	public static Path createWritableFolderIfNotExist(String folderDesc, String folder){
		// Make sure journal folder exists, if not exist, where should I create the folder
		if (Strings.isNullOrEmpty(folder)){
			throw new IllegalArgumentException("Folder is missing from config [folder:" + folder + ", desc:" + folderDesc + "]");
		}
		
		Path path = Paths.get(folder);
		if (Files.notExists(path)){
			try {
				Files.createDirectory(path);
			} 
			catch (Exception e) {
				throw new IllegalArgumentException("Could not create folder [folder:" + folder + "]");
			}
		}
		
		if (!Files.isDirectory(path)){
			throw new IllegalArgumentException("Input path is not a folder [path:" + folder + "]");
		}
		
		// Check if writable by writing then delete a file
		try {
			Path createTempFile = Files.createTempFile(path, "test-access", null);
			Files.delete(createTempFile);
		} 
		catch (Exception e){
			throw new IllegalArgumentException("Insufficient access right in path [folder:" + folder + "]", e);
		}
		return path;
	}
	
	public static void deleteRecursive(Path target, boolean keepTargetDir) throws IOException{
		deleteRecursive(target);
		if (!keepTargetDir){
			target.toFile().delete();
		}
	}

	/**
	 * BLOCKING ALERT
	 * 
	 * @param directory
	 * @throws IOException
	 */
	private static void deleteRecursive(final Path directory) throws IOException{
		for (File file : directory.toFile().listFiles()){
			Path path = file.toPath();
			if (Files.isDirectory(path)){
				deleteRecursive(path);
			}
			file.delete();
		}
	}
	
	public static void copyRecursive(Path source, Path target, boolean preserve, boolean overwrite) throws IOException{
		LOG.debug("copy recursive from {} to {}, preserve {}, overwrite {}", source, target, preserve, overwrite);
		boolean isTargetDir = Files.isDirectory(target);
		Path dest = (isTargetDir) ? target.resolve(source.getFileName()) : target;
		EnumSet<FileVisitOption> ops = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		TreeCopier tc = new TreeCopier(source, dest, true, false);
		Files.walkFileTree(source, ops, Integer.MAX_VALUE, tc);
		LOG.debug("done copy recursive from {} to {}", source, target);
	}

	public static void deleteDirectory(Path directory) throws IOException{
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.out.println("Delete: " + file.toString());
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				try {
				System.out.println("Delete folder: " + dir);
				
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
				}
				catch (Exception ex){
					return FileVisitResult.CONTINUE;
				}
			}

		});		
	}
	
	static class TreeCopier implements FileVisitor<Path> {
		private final Path source;
		private final Path target;
		private final boolean preserve;
		private final boolean overwriteIfExists;
		
		TreeCopier(Path source, Path target, boolean preserve, boolean overwrite){
			this.source = source;
			this.target = target;
			this.preserve = preserve;
			this.overwriteIfExists = overwrite;
		}
		
		
		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			// fix up modification time of directory when done
			if (exc == null && this.preserve){
				Path newDir = this.target.resolve(this.source.relativize(dir));
				try {
					FileTime time = Files.getLastModifiedTime(dir);
					Files.setLastModifiedTime(newDir, time);
				}
				catch (IOException ex){
					LOG.warn("unable to copy all attributes to " + newDir.toString(), ex);
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
			CopyOption[] options = (this.preserve) ? new CopyOption[]{ COPY_ATTRIBUTES } : new CopyOption[0];
			Path newDir = this.target.resolve(this.source.relativize(dir));
			try{
				Files.copy(dir, newDir, options);
				LOG.debug("created folder: {}", newDir);
			}
			catch (FileAlreadyExistsException ex){
				// ignore
			}
			catch (IOException ex){
				LOG.error("unable to create " + newDir.toString(), ex);
				return FileVisitResult.SKIP_SUBTREE;
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path from, BasicFileAttributes attrs) throws IOException {
			CopyOption[] options = (preserve) ? new CopyOption[]{ COPY_ATTRIBUTES, REPLACE_EXISTING } : 
				new CopyOption[]{ REPLACE_EXISTING };
			Path to = target.resolve(source.relativize(from));  
			if (Files.notExists(to) || this.overwriteIfExists){
				Files.copy(from, to, options);
				LOG.debug("copied file: {}", to);
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			if (exc instanceof FileSystemLoopException){
				LOG.error("cycle detected: {}", file);
			}
			else {
				LOG.error("unable to copy: " + file.toString(), exc);
			}
			return null;
		}
	}
}
