//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using toolkit latest

import core.*
import tools.*
import util.*
import os.*

lazy val downloadPath = os.pwd / "osboxes" / "downloads"

//TODO think about introducing a "meta tool"
object osboxes extends BuiltInTool("osboxes", RequiredVersion.any(p7zip)):
  override def postInstall(requiredVersion: RequiredVersion) =
    println("Downloading default VM image...")
    vmImage.ubuntu23_04.download()
    println("Finished downloading default VM image!")

  enum vmImage(downloadable: DownloadableFile, vdiFile: String, expectedVdiSha256: String):
    case ubuntu23_04
        extends vmImage(
          DownloadableFile(
            name = "Ubuntu 23.04 Lunar Lobster.7z",
            url =
              "https://downloads.sourceforge.net/project/osboxes/v/vb/55-U-u/23.04/64bit.7z?ts=gAAAAABlJQqzW7hKBaFIDgqyIWFBxHHnRs-3Q-W53ilVdrFxkEi8eVVgc900gfGs2CdHANs_32Rq62GqAEM1o1C4Dmirct3PpA%3D%3D&r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fosboxes%2Ffiles%2Fv%2Fvb%2F55-U-u%2F23.04%2F64bit.7z%2Fdownload%3Fuse_mirror%3Dautoselect",
            expectedSha256sum = "8a139e38b3713e2edea2ead80ebfd80e4478cdba750c518008ce89321b2862cd",
          ),
          vdiFile = "Ubuntu 23.04 (64bit).vdi",
          expectedVdiSha256 = "9073c2e58f95fba2c8f6816bafb1eead70ef635de87903fc74d9d15c2974853e",
        )
    lazy val vdiFilePath = downloadPath / vdiFile
    def download() =
      val downloadFilePath    = downloadPath / downloadable.name
      val extractedFolderPath = downloadPath / "64bit"
      if exists(vdiFilePath) then
        println("Already downloaded " + vdiFilePath)
        // if shasum.sha256sumCheck(vdiFilePath, expectedVdiSha256) then println("Unchanged " + vdiFilePath)
        // else
        //   throw new Exception("Think about removing " + vdiFilePath + " and re-running this script")
      if !exists(vdiFilePath) then
        println("Downloading " + vdiFilePath)
        makeDir.all(downloadPath)
        curl.download(downloadable, Some(downloadFilePath))
        if !exists(extractedFolderPath) then
          println("Extracting " + downloadFilePath)
          p7zip.extract(downloadFilePath, downloadPath)
          move(extractedFolderPath / vdiFile, vdiFilePath)
        println("Downloaded " + vdiFilePath)
      if exists(extractedFolderPath) then remove.all(extractedFolderPath)
      // we keep the downloadFilePath around in case we need to re-extract the vdiFile

      // TODO think about using `Using` so we hide the removal of files under the close() method
