
//! Bus Timetable PDF → CSV Extractor  (Rust)
//! ==========================================
//!
//! Cargo.toml dependencies
//! ```toml
//! [dependencies]
//! regex = "1"
//! ```
//!
//! System dependency
//!   sudo apt install poppler-utils   # provides pdftotext
//!
//! Run
//!   cargo run -- input.pdf [output.csv]
 
use std::{
    env,
    fs::File,
    io::{self, BufWriter, Write},
    path::Path,
    process::Command,
};
 
use regex::Regex;
 
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("Usage: {} <input.pdf> [output.csv]", args[0]);
        std::process::exit(1);
    }
 
    let pdf_path = &args[1];
    let csv_path = if args.len() > 2 {
        args[2].clone()
    } else {
        Path::new(pdf_path)
            .with_extension("csv")
            .to_string_lossy()
            .into_owned()
    };
 
    let rows = extract_rows(pdf_path)?;
 
    if rows.is_empty() {
        eprintln!("No timetable data found.");
        std::process::exit(1);
    }
 
    write_csv(&rows, &csv_path)?;
    println!("✓ {} stops written to '{}'", rows.len(), csv_path);
    Ok(())
}
 
/// Extract timetable rows from a PDF using pdftotext (poppler).
///
/// pdftotext -layout preserves the columnar reading order, giving us clean
/// lines that are straightforward to parse with a time-token regex.
fn extract_rows(pdf_path: &str) -> Result<Vec<Vec<String>>, Box<dyn std::error::Error>> {
    // "-layout" keeps spatial layout; "-" sends output to stdout
    let output = Command::new("pdftotext")
        .args(["-layout", pdf_path, "-"])
        .output()
        .map_err(|e| format!("Failed to run pdftotext: {e}. Is poppler-utils installed?"))?;
 
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("pdftotext error: {stderr}").into());
    }
 
    let text = String::from_utf8(output.stdout)?;
    let time_re = Regex::new(r"\b(\d{1,2}:\d{2})\b")?;
    let mut rows: Vec<Vec<String>> = Vec::new();
 
    for raw_line in text.lines() {
        let line = raw_line.trim();
 
        let mut times: Vec<String> = Vec::new();
        let mut first_start: Option<usize> = None;
 
        for m in time_re.find_iter(line) {
            if first_start.is_none() {
                first_start = Some(m.start());
            }
            times.push(m.as_str().to_owned());
        }
 
        let Some(start) = first_start else { continue };
 
        let stop_name = line[..start].trim().to_owned();
        if stop_name.is_empty() {
            continue;
        }
 
        let mut row = Vec::with_capacity(1 + times.len());
        row.push(stop_name);
        row.extend(times);
        rows.push(row);
    }
 
    Ok(rows)
}
 
/// Write extracted rows to a UTF-8 CSV file with a generated header.
fn write_csv(rows: &[Vec<String>], csv_path: &str) -> io::Result<()> {
    let max_services = rows.iter().map(|r| r.len() - 1).max().unwrap_or(0);
 
    let mut header: Vec<String> = Vec::with_capacity(1 + max_services);
    header.push("Paragem".to_owned());
    for i in 1..=max_services {
        header.push(format!("Serviço_{i}"));
    }
 
    let file = File::create(csv_path)?;
    let mut writer = BufWriter::new(file);
 
    write_csv_line(&mut writer, &header)?;
    for row in rows {
        write_csv_line(&mut writer, row)?;
    }
 
    writer.flush()
}
 
/// Minimal RFC 4180 CSV serialisation for a single row.
fn write_csv_line<W: Write>(writer: &mut W, fields: &[String]) -> io::Result<()> {
    let escaped: Vec<String> = fields
        .iter()
        .map(|f| {
            if f.contains(',') || f.contains('"') || f.contains('\n') {
                format!("\"{}\"", f.replace('"', "\"\""))
            } else {
                f.clone()
            }
        })
        .collect();
 
    writeln!(writer, "{}", escaped.join(","))
}
