use std::fs::File;
use std::path::Path;

use std::io::{Read, Write, BufReader};
use std::time::{Instant, Duration};
use std::ops::Deref;
use crypto_hash::Hasher;
use sha1::Digest;
use std::sync::{mpsc, Arc, Mutex};
use std::thread::sleep;

fn main() -> Result<(), std::io::Error> {
//    let path = Path::new("s:\\SDCARD.TAR");
    let path = Path::new("d:\\7om81lvahae61.jpg");
    println!("Path: {}", path.display());

    /// Print digest result as hex string and name pair
    fn print_result(sum: &[u8], name: &str) {
        for byte in sum {
            print!("{:02x}", byte);
        }
        println!("\t{}", name);
    }

    const BUFFER_SIZE: usize = 8 * 1024;

    let k = 1;

    // for i in 0..k {
    //     use crypto_hash::{Algorithm, digest};
    //
    //     let start = Instant::now();
    //
    //     let (sender, receiver) = mpsc::sync_channel::<([u8; BUFFER_SIZE], usize)>(16);
    //
    //     let join_handle = std::thread::spawn(move || -> std::io::Result<()> {
    //         let mut bb = [0; BUFFER_SIZE];
    //         let mut file = File::open(&path)?;
    //         loop {
    //             let n = file.read(&mut bb)?;
    //             if n == 0 {
    //                 break;
    //             }
    //
    //
    //             // Send a message
    //             sender.send((bb.clone(), n)).unwrap();
    //         }
    //         Ok(())
    //     });
    //
    //     let mut hasher = Hasher::new(Algorithm::SHA1);
    //
    //     // Receive messages until end
    //
    //     while let Ok((bb, n)) = receiver.recv() {
    //         hasher.write_all(&bb[..n])?;
    //
    //     }
    //
    //     match join_handle.join(){
    //         Ok(Ok(())) => println!("Thread succeeded"),
    //         Ok(Err(err)) => panic!(err),
    //         Err(eaoeu) => panic!(eaoeu)
    //     }
    //
    //     let duration = start.elapsed();
    //
    //     let hash = hasher.finish();
    //
    //
    //     println!("Run 1 {}: Took {:?}",i, duration);
    //     print_result(&hash, "1");
    // }

    for i in 0..k {
        use crypto_hash::{Algorithm, digest};

        let start = Instant::now();

        use std_semaphore::Semaphore;

        let sem = Semaphore::new(0);
        sem.acquire();
        {
            let _guard = sem.access();
            println!("is: {}", _guard);
            // ...
        } // resources is released here
        sem.release();

        // let readLock = Arc::new(Mutex::new(0));
        // let readLockReader = Arc::clone(&readLock);

        // let hashLock = Arc::new(Mutex::new(n));
        // let hashLockReader = Arc::clone(&hashLock);

        // let join_handle = std::thread::spawn(move || -> std::io::Result<()> {
        //
        //     for i in 1..5 {
        //         let mut n = readLockReader.lock().unwrap();
        //         *n += i;
        //         sleep(std::time::Duration::from_millis(3000));
        //         drop(n);
        //     }
        //
        //     let mut n = readLockReader.lock().unwrap();
        //     *n = 0;
        //     sleep(std::time::Duration::from_millis(3000));
        //     drop(n);
        //
        //     Ok(())
        // });
        //
        // loop {
        //     let n = readLock.lock().unwrap();
        //     println!("N: {}", *n);
        //     let m = *n;
        //     drop(n);
        //     if (m == 0) {
        //         break;
        //     }
        // }
        //
        // match join_handle.join(){
        //     Ok(Ok(())) => println!("Thread succeeded"),
        //     Ok(Err(err)) => panic!(err),
        //     Err(eaoeu) => panic!(eaoeu)
        // }
        //
        // {
        //     let n = readLock.lock().unwrap();
        //     println!("N: {}", *n);
        //     drop(n);
        // }


  //       let mut bbx = [0; BUFFER_SIZE];
  //       let readLock = Arc::new(Mutex::new(bbx));
  //       let readLockReader = Arc::clone(&readLock);
  //
  //       let mut n = 0;
  //       let hashLock = Arc::new(Mutex::new(n));
  //       let hashLockReader = Arc::clone(&hashLock);
  //
  //       let join_handle = std::thread::spawn(move || -> std::io::Result<()> {
  //           let mut file = File::open(&path)?;
  //           loop {
  //               println!("WAITING FOR READ ...");
  //               let mut lock_bbx = readLockReader.lock().unwrap();
  //
  //               println!("READING ...");
  //               let n = file.read(&mut lock_bbx)?;
  //               sleep(std::time::Duration::from_millis(3000));
  //               if n == 0 {
  //                   break;
  //               }
  //               println!("READ {}", n);
  //
  //               println!("SENDING {}", n);
  //               drop(lock);
  //
  //
  //               // Send a message
  // //              sender.send(n).unwrap();
  //               println!("SENT {}", n);
  //           }
  //           Ok(())
  //       });
  //
  //       let mut hasher = Hasher::new(Algorithm::SHA1);
  //
  //       loop {
  //           println!("WAITING FOR DATA ...");
  //           let mut data = hashLock.lock().unwrap();
  //           println!("HASHER RECEIVED: {}", n);
  //           sleep(std::time::Duration::from_millis(5000));
  //           hasher.write_all(&bbx[..n])?;
  //           drop(data);
  //           println!("HASHER PROCESSED: {}", n);
  //       }
  //
  //       // Receive messages until end
  //
  //       // while let Ok((n)) = receiver.recv() {
  //       //     println!("HASHER RECEIVED: {}", n);
  //       //     sleep(std::time::Duration::from_millis(5000));
  //       //     hasher.write_all(&bbx[..n])?;
  //       //     println!("HASHER PROCESSED: {}", n);
  //       // }
  //
  //       match join_handle.join(){
  //           Ok(Ok(())) => println!("Thread succeeded"),
  //           Ok(Err(err)) => panic!(err),
  //           Err(eaoeu) => panic!(eaoeu)
  //       }
  //
  //       let duration = start.elapsed();
  //
  //       let hash = hasher.finish();
  //
  //
  //       println!("Run 2 {}: Took {:?}",i, duration);
  //       print_result(&hash, "2");
    }

    // for i in 0..k {
    //     let mut bb = [0; BUFFER_SIZE];
    //     use crypto_hash::{Algorithm, digest};
    //     let mut hasher = Hasher::new(Algorithm::SHA1);
    //
    //     let mut file = File::open(&path)?;
    //     let start = Instant::now();
    //     loop {
    //
    //
    //         let n = file.read(&mut bb)?;
    //         if n == 0 {
    //             break;
    //         }
    //         hasher.write_all(&bb[..n])?;
    //     }
    //     let duration = start.elapsed();
    //
    //     let hash = hasher.finish();
    //     println!("Run 3 {}: Took {:?}",i, duration);
    //     print_result(&hash, "3");
    // }

/*
    for i in 0..3 {
        use crypto_hash::{Algorithm, digest};

        use sha1::{Sha1, Digest};
        let mut sha1 = sha1::Sha1::new();

        let mut file = File::open(&path)?;
        let mut reader = BufReader::new(&mut file);

        let start = Instant::now();
        std::io::copy(&mut reader, &mut sha1)?;
        let duration = start.elapsed();

        let hash = sha1.finalize();
        println!("Run {}: Took {:?}",i, duration);
        print_result(&hash.as_slice(), "BF");
    }
*/
    Ok(())
}
