/*
 * Bus Timetable PDF → CSV Extractor  (C# / .NET 8 / PdfPig)
 * ===========================================================
 * Add the NuGet package
 *   dotnet add package PdfPig
 *
 * Minimal .csproj (if starting from scratch)
 *   dotnet new console -n TimetableExtractor
 *   cd TimetableExtractor
 *   dotnet add package PdfPig
 *   # replace Program.cs with this file
 *
 * Run
 *   dotnet run -- input.pdf [output.csv]
 */

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using UglyToad.PdfPig;
using UglyToad.PdfPig.DocumentLayoutAnalysis.TextExtractor;

class TimetableExtractor
{
    /// <summary>Matches time tokens such as 7:40 or 10:00.</summary>
    static readonly Regex TimeRe = new(@"\b(\d{1,2}:\d{2})\b", RegexOptions.Compiled);

    static void Main(string[] args)
    {
        if (args.Length < 1)
        {
            Console.Error.WriteLine("Usage: TimetableExtractor <input.pdf> [output.csv]");
            Environment.Exit(1);
        }

        string pdfPath = args[0];
        string csvPath = args.Length > 1
            ? args[1]
            : Path.ChangeExtension(pdfPath, ".csv");

        List<string[]> rows = ExtractRows(pdfPath);

        if (rows.Count == 0)
        {
            Console.Error.WriteLine("No timetable data found.");
            Environment.Exit(1);
        }

        WriteCsv(rows, csvPath);
        Console.WriteLine($"✓ {rows.Count} stops written to '{csvPath}'");
    }

    /// <summary>
    /// Open the PDF and extract timetable rows.
    ///
    /// Strategy: use PdfPig's ContentOrderTextExtractor to get page text in reading
    /// order, then process each line exactly as the Python/Java versions do.
    /// </summary>
    static List<string[]> ExtractRows(string pdfPath)
    {
        var rows = new List<string[]>();

        using var doc = PdfDocument.Open(pdfPath);
        foreach (var page in doc.GetPages())
        {
            // ContentOrderTextExtractor preserves reading order and inserts newlines
            // at line boundaries — ideal for columnar timetable layouts.
            string text = ContentOrderTextExtractor.GetText(page);

            foreach (string rawLine in text.Split('\n'))
            {
                string line = rawLine.Trim();
                MatchCollection matches = TimeRe.Matches(line);
                if (matches.Count == 0) continue;

                int firstStart = matches[0].Index;
                string stopName = line[..firstStart].Trim();
                if (string.IsNullOrWhiteSpace(stopName)) continue;

                var row = new string[1 + matches.Count];
                row[0] = stopName;
                for (int i = 0; i < matches.Count; i++)
                    row[i + 1] = matches[i].Value;

                rows.Add(row);
            }
        }

        return rows;
    }

    /// <summary>Write extracted rows to a UTF-8 BOM CSV file with a generated header.</summary>
    static void WriteCsv(List<string[]> rows, string csvPath)
    {
        int maxServices = 0;
        foreach (var r in rows)
            if (r.Length - 1 > maxServices) maxServices = r.Length - 1;

        var header = new string[1 + maxServices];
        header[0] = "Paragem";
        for (int i = 1; i <= maxServices; i++) header[i] = $"Serviço_{i}";

        // UTF-8 BOM so Excel opens it correctly without import wizards
        using var writer = new StreamWriter(csvPath, append: false, new UTF8Encoding(encoderShouldEmitUTF8Identifier: true));
        writer.WriteLine(ToCsvLine(header));
        foreach (var row in rows)
            writer.WriteLine(ToCsvLine(row));
    }

    /// <summary>Minimal RFC 4180 CSV serialisation for a single row.</summary>
    static string ToCsvLine(string[] fields)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < fields.Length; i++)
        {
            if (i > 0) sb.Append(',');
            string f = fields[i] ?? "";
            if (f.Contains(',') || f.Contains('"') || f.Contains('\n'))
                sb.Append('"').Append(f.Replace("\"", "\"\"")).Append('"');
            else
                sb.Append(f);
        }
        return sb.ToString();
    }
}
