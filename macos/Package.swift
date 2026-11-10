// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DeskFlow",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "DeskFlow", targets: ["DeskFlow"])
    ],
    targets: [
        .executableTarget(
            name: "DeskFlow",
            path: "DeskFlow"
        ),
        .testTarget(
            name: "DeskFlowTests",
            dependencies: ["DeskFlow"],
            path: "Tests"
        )
    ]
)
