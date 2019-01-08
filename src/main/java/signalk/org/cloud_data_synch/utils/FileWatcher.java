/**
 * 
 */
package signalk.org.cloud_data_synch.utils;

import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_DATA_LOCATION;
import static signalk.org.cloud_data_synch.utils.ConfigUtils.DEFAULT_FILE_WATCHING_PERIOD;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
//import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import signalk.org.cloud_data_synch.service.CloudSynchService;

import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.StandardWatchEventKinds;

/**
 * @author GD85376
 *
 */
public class FileWatcher
{

	private WatchService watchService;

	private static Logger logger = LogManager.getLogger(CloudSynchService.class);

	/**
	 * Creates a WatchService and registers the given directory
	 */
	public FileWatcher() throws IOException {
		this.watchService = FileSystems.getDefault().newWatchService();
	}

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event)
	{
		return (WatchEvent<T>) event;
	}

	/**
	 * https://howtodoinjava.com/java8/java-8-watchservice-api-tutorial/
	 * 
	 * @param dirName
	 * @return
	 */
	/**
	 * 
	 * @param dirName -- dir name where file resides
	 * @param events (StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
			        StandardWatchEventKinds.ENTRY_MODIFY);
	 * @return file name
	 */
	public <T> String directoryWatcher1(String dirName, Kind<T>... events)
	{
		try {
			java.nio.file.Path folder = Paths.get(dirName);

			WatchKey key = folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
			        StandardWatchEventKinds.ENTRY_MODIFY);

			while ((key = watchService.take()) != null) {
				for (WatchEvent<?> event : key.pollEvents()) {
					@SuppressWarnings("rawtypes")
					WatchEvent.Kind kind = event.kind();

					@SuppressWarnings("unchecked")
					java.nio.file.Path name = (java.nio.file.Path) ((WatchEvent<Path>) event).context();
					folder.resolve(name);
					// Path child = folder.resolve(name);
					// System.out.format("%s: %s\n", event.kind().name(),child);
					//System.out.format("%s: %s\n", ev.kind(), dir.resolve(ev.context()));

					if (logger.isDebugEnabled())
						logger.debug("Event kind: {}. File affected: {}", event.kind(), event.context());

					return event.context().toString();
				}
				key.reset();
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public <T> String directoryWatcher(String dirName, Kind<T>... events) throws IOException
	{
		java.nio.file.Path folder = Paths.get(dirName);

		WatchKey _key = folder.register(watchService, events);

		try {
			WatchKey key = watchService.poll();
			if (null == key)
				// if ((key = watcher.poll()) == null)
				return null;

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();

				if (logger.isDebugEnabled())
					logger.debug("File Watcher Event {}",kind);

				if (kind == StandardWatchEventKinds.OVERFLOW)
					continue;

				@SuppressWarnings("unchecked")
				WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;
				java.nio.file.Path dataFile = ev.context();
				if (logger.isInfoEnabled())
					logger.info("Found file {}. Will process now", dataFile);

				boolean valid = key.reset();

				if (!valid)
					logger.error("Invalid key => {}. Chugging continue...", key);

				return dataFile.toString();

			}
		}
		catch (Exception e) {
			logger.error("File Watcher Error : {}", e.getLocalizedMessage());
			throw new IOException(e.getLocalizedMessage());
		}
		return  null;
	}
	public void startDirectoryLister(String watchDir)
	{
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
		{
			boolean stop = false;

			@Override
			public void run()
			{
				try {
					
//					https://www.baeldung.com/java-8-streams-introduction
					List aa = new ArrayList<String>();
					Files.newDirectoryStream(Paths.get(watchDir),
//					          path ->
//					          path.toString()).forEach(file);
							path -> path.toString().endsWith("." + ConfigUtils.DEFAULT_DATA_FILE_EXTENTION)).forEach((_f) -> {
						        System.out.println(_f);
						        aa.add(_f);
					        	CloudSynchService.getDataFilesQueue().enqueue(_f.getFileName().toString());
					        }) ;
///					aa.parallelStream().forEach(el -> doWork(el));
				}
				catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					stop = true;
				}
			}
		}, 0, DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS);
	}
	
	@SuppressWarnings("unchecked")
	public <T> void startFileWatcher(String watchDir, Kind<T>... events)
	{
		try {
			Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
			{
				boolean stop = false;

				@Override
				public void run()
				{
					while (!stop) {
						try {
							String dataFile = directoryWatcher(watchDir, StandardWatchEventKinds.ENTRY_CREATE,
							        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

							if (logger.isInfoEnabled())
								logger.info("Found file {}. Will process now", dataFile);

							CloudSynchService.getDataFilesQueue().enqueue(DEFAULT_DATA_LOCATION + "/" + dataFile);

							if (ConfigUtils.getServerType().equals(ConfigUtils.PRODUCER))
								// produce();
								System.out.println("Will produce file here");
							else if (ConfigUtils.getServerType().equals(ConfigUtils.CONSUMER))
								System.out.println("Will consume file here");
							// consume();
						}
						catch (Exception e) {
							logger.error("{}", e.getLocalizedMessage());
							stop = true;
							// break;
						}
					}
				}
			}, 0, DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public <T> void startFileWatcherOLD(String watchDir, Kind<T>... events)
	{
		try {
			WatchService watcher = FileSystems.getDefault().newWatchService();
			java.nio.file.Path folder = Paths.get(watchDir);

			WatchKey _key = folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
			        StandardWatchEventKinds.ENTRY_MODIFY);

			Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
			{
				boolean stop = false;

				@Override
				public void run()
				{
					while (!stop) {
						try {
							WatchKey key = watcher.poll();
							if (null == key)
								// if ((key = watcher.poll()) == null)
								return;

							for (WatchEvent<?> event : key.pollEvents()) {
								WatchEvent.Kind<?> kind = event.kind();

								if (logger.isDebugEnabled())
									logger.debug("File Watcher Event {}",kind);

								if (kind == StandardWatchEventKinds.OVERFLOW)
									continue;

								@SuppressWarnings("unchecked")
								WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;
								java.nio.file.Path dataFile = ev.context();
								if (logger.isInfoEnabled())
									logger.info("Found file {}. Will process now", dataFile);

								CloudSynchService.getDataFilesQueue().enqueue(DEFAULT_DATA_LOCATION + "/" + dataFile);
								
								if (ConfigUtils.getServerType().equals(ConfigUtils.PRODUCER))
//									produce();
									System.out.println("Will produce file here");
								else if (ConfigUtils.getServerType().equals(ConfigUtils.CONSUMER))
									System.out.println("Will consume file here");
//									consume();

								boolean valid = key.reset();
								if (!valid)
									logger.error("Invalid key => {}. Chugging continue...", key);
							}
						}
						catch (Exception e) {
							logger.error("File Watcher Error : {}", e.getLocalizedMessage());
							stop = true;
							// break;
						}
					}
				}
			}, 0, DEFAULT_FILE_WATCHING_PERIOD, TimeUnit.MILLISECONDS);
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Thread t = new Thread(r, "dataFileWatcher");
		// t.setDaemon(true);
		// t.start();

	}
	

}
