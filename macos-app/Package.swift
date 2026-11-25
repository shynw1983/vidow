// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "Vidow",
    defaultLocalization: "en",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "Vidow", targets: ["Vidow"])
    ],
    targets: [
        .executableTarget(
            name: "Vidow",
            path: "Sources/Vidow"
        )
    ]
)
