package duplicatefilefinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DuplicateFileFinder {

	public static void main(String[] args) throws Exception {
		DuplicateFileFinder instance = new DuplicateFileFinder();
		if (args.length == 1) {
			instance.run(args[0], false);
		} else if (args.length == 2) {
			instance.run(args[0], "delete".equals(args[1]));
		} else {
			instance.usage();
		}
	}

	private void usage() {
		System.out.println("Usage: directory [delete]");
		System.out.println("e.g.   picture");
		System.out.println("e.g.   picture delete");
	}

	private void run(String directoryPath, boolean delete) throws Exception {
		// recordMap contains all files indexed by file size
		Map<Long, List<Record>> recordMap = new HashMap<>();
		exploreDirectory(recordMap, new File(directoryPath));

		// checksumMap contains all files indexed by checksum value
		Map<String, List<Record>> checksumMap = new HashMap<>();
		for (Map.Entry<Long, List<Record>> entry : recordMap.entrySet()) {
			List<Record> recordList = entry.getValue();

			// only consider a list if there are more than 1 file in it, if there is only 1 file, then it can't be a duplicate
			// all files in this list have the same sizes
			if (recordList.size() > 1) {
				for (Record record : recordList) {
					// calculate checksum for each file
					record.setChecksum(checksum(record.getFile()));

					// index by checksum
					List<Record> checksumList = checksumMap.get(record.getChecksum());
					if (checksumList == null) {
						checksumMap.put(record.getChecksum(), checksumList = new ArrayList<>());
					}
					checksumList.add(record);
				}
			}
		}

		for (Map.Entry<String, List<Record>> entry : checksumMap.entrySet()) {
			List<Record> checksumList = entry.getValue();
			if (checksumList.size() > 1) {
				Collections.sort(checksumList, new Comparator<Record>() {
					public int compare(Record r1, Record r2) {
						return new Integer(r1.getFile().getAbsolutePath().length()).compareTo(new Integer(r2.getFile().getAbsolutePath().length()));
					}
				});
			}

			// only consider a list if there are more than 1 file in it, if there is only 1 file, then it can't be a duplicate
			// all files in this list have the same checksums, they are probably duplicates
			while (checksumList.size() > 1) {
				// compare each file with each other, removing them from the list if they are identical
				Record reference = checksumList.remove(0);
				boolean printed = false;
				Iterator<Record> iterator = checksumList.iterator();
				while (iterator.hasNext()) {
					Record record = iterator.next();
					if (same(reference, record)) {
						if (!printed) {
							System.out.println("\nDuplicates: ");
							System.out.println("\t" + reference.getFile().getAbsolutePath());
						}
						printed = true;
						

						if (delete) {
							System.out.println("\tDeleting " + record.getFile().getAbsolutePath());
							record.getFile().delete();
						} else {
							System.out.println("\t" + record.getFile().getAbsolutePath());
						}

						iterator.remove();
					}
				}
			}
		}
	}

	private boolean same(Record reference, Record record) {
		return same(reference.getFile(), record.getFile());
	}

	// what about using another digest algorithm instead of comparing contents ? SHA-256 perhaps, it's highly unlikely to get a collision on BOTH SHA-1 and SHA-256
	public static boolean same(File reference, File record) {
		int size = 10240;
		byte[] buffer = new byte[size];

		ByteBuffer referenceBuffer = ByteBuffer.allocate(size);
		ByteBuffer recordBuffer = ByteBuffer.allocate(size);

		FileInputStream referenceFis = null;
		FileInputStream recordFis = null;

		try {
			referenceFis = new FileInputStream(reference);
			recordFis = new FileInputStream(record);

			int referenceRead = 0;
			int recordRead = 0;

			while (referenceRead != -1 && recordRead != -1) {
				if ((referenceRead = referenceFis.read(buffer)) != -1) {
					referenceBuffer.put(buffer, 0, referenceRead);
				}

				if ((recordRead = recordFis.read(buffer)) != -1) {
					recordBuffer.put(buffer, 0, recordRead);
				}

				if (referenceRead == recordRead) {
					if (referenceRead == -1 && recordRead == -1) {
						break;
					} else {
						referenceBuffer.rewind();
						recordBuffer.rewind();
						if (referenceBuffer.compareTo(recordBuffer) == 0) {

						} else {
							return false;
						}
					}
				} else {
					return false;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (referenceFis != null) {
				try {
					referenceFis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (recordFis != null) {
				try {
					recordFis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return true;
	}

	private void exploreDirectory(Map<Long, List<Record>> recordMap, File directory) {
		if (directory.isDirectory()) {
			for (File f : directory.listFiles()) {
				if (f.isDirectory()) {
					exploreDirectory(recordMap, f);
				} else {
					process(recordMap, f);
				}
			}
		}
	}

	private void process(Map<Long, List<Record>> recordMap, File f) {
		Record record = new Record();
		record.setFile(f);
		record.setSize(f.length());

		List<Record> recordList = recordMap.get(record.getSize());
		if (recordList == null) {
			recordMap.put(record.getSize(), recordList = new ArrayList<>());
		}
		recordList.add(record);
	}

	public String checksum(File f) throws Exception {
		// MessageDigest.getInstance("MD5");
		// MessageDigest.getInstance("SHA-256");
		MessageDigest md = MessageDigest.getInstance("SHA-1");

		byte[] dataBytes = new byte[5120];
		FileInputStream fis = new FileInputStream(f);
		int n = 0;
		while ((n = fis.read(dataBytes)) != -1) {
			md.update(dataBytes, 0, n);
		}
		fis.close();

		// String.format("%032x", new BigInteger(1, mdMD5.digest()));
		// String.format("%064x", new BigInteger(1, mdSHA256.digest()));
		String checksum = String.format("%040x", new BigInteger(1, md.digest()));

		return checksum;
	}

}

class Record {

	protected File file;
	protected Long size;
	protected String checksum;

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

}
