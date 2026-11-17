import Foundation
import Vision
import CoreGraphics
import ImageIO

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data(("[] " + message + "\n").utf8))
    exit(1)
}

guard CommandLine.arguments.count >= 2 else {
    fail("falta la ruta de la imagen")
}

let imagePath = CommandLine.arguments[1]

guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: imagePath) as CFURL, nil) else {
    fail("no se pudo abrir la imagen")
}

guard let cgImage = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fail("no se pudo decodificar la imagen")
}

let imageWidth = CGFloat(cgImage.width)
let imageHeight = CGFloat(cgImage.height)

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate

let requestHandler = VNImageRequestHandler(cgImage: cgImage, options: [:])
do {
    try requestHandler.perform([request])
} catch {
    fail("fallo el reconocimiento: \(error.localizedDescription)")
}

let results = request.results ?? []
let observations = results

var entries: [[String: Any]] = []

for observation in observations {
    guard let top = observation.topCandidates(1).first else { continue }
    let bbox = observation.boundingBox
    let entry: [String: Any] = [
        "text": top.string,
        "x": Double(bbox.minX * imageWidth),
        "y": Double((1 - bbox.maxY) * imageHeight),
        "width": Double(bbox.width * imageWidth),
        "height": Double(bbox.height * imageHeight),
        "confidence": Double(top.confidence)
    ]
    entries.append(entry)
}

if entries.isEmpty {
    print("[]")
} else {
    let data = try JSONSerialization.data(withJSONObject: entries)
    print(String(data: data, encoding: .utf8)!)
}
