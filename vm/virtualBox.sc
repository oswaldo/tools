//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using file "../osboxes/osboxes.sc"
//> using toolkit latest

import core.*
import tools.*
import osboxes.*
import util.*
import os.*
import java.util.UUID

enum virtualBoxImage(val vmImage: vmImage, val vmName: String, val osType: String):
  case ubuntu23_04
      extends virtualBoxImage(
        vmImage.ubuntu23_04,
        vmName = "Constructed Ubuntu 23.04 Lunar Lobster",
        osType = "Ubuntu_64",
      )

object virtualBox extends Tool("vboxmanage"):
  override def install(requiredVersion: RequiredVersion): Unit =
    val arch = uname.arch()
    println(s"Installing $name for arch $arch")
    arch match
      case "arm64" =>
        val downloadedFilePath = curl.download(
          DownloadableFile(
            name = "VirtualBox-7.0.8_BETA4-156879-macOSArm64.dmg",
            url = "https://download.virtualbox.org/virtualbox/7.0.8/VirtualBox-7.0.8_BETA4-156879-macOSArm64.dmg",
            expectedSha256sum = "7c24aa0d40ae65cde24d1fba5a2c2fe49a6f6c7d42b01cf3169a7e3459b80b8d",
          ),
        )
        installer.installDmg(downloadedFilePath, "VirtualBox.pkg")
      case _ =>
        brew.installFormula("virtualbox")

  override def installedVersion(): InstalledVersion =
    tryRunLines("--version") match
      case Success(v) =>
        InstalledVersion.Version(v.head.trim().replace("_", "-").replace("BETA", "BETA.").replace("r", "+r"))
      case _ => InstalledVersion.Absent

  case class RegisteredVm(val name: String, val uuid: UUID)

  def listVms(): List[RegisteredVm] =
    tryRunLines("list", "vms") match
      case Success(lines) =>
        lines.map { line =>
          val name = line.split("\"").toList match
            case _ :: name :: _ => name
            case _              => ""
          val uuid = line.split(" \\{").toList match
            case _ :: uuid :: _ => uuid.stripSuffix("}")
            case _              => ""
          println("Found VM: " + name + " with UUID " + uuid)
          RegisteredVm(name, UUID.fromString(uuid))
        }
      case e =>
        println("Could not find VMs: " + e)
        Nil

  def createVm(image: virtualBoxImage): Option[RegisteredVm] =
    listVms().find(_.name == image.vmName) match
      case Some(vm) =>
        println("VM already created: " + vm)
        None
      case None =>
        image.vmImage.download()
        val lines = runLines(
          "createvm",
          "--name",
          image.vmName,
          "--ostype",
          image.osType,
          "--register",
        )
        val uuidLinePrefix = "UUID: "
        val uuid = lines
          .find(_.startsWith(uuidLinePrefix))
          .map(_.stripPrefix(uuidLinePrefix).trim())
          .map(UUID.fromString)
          .getOrElse(throw new Exception("Could not find UUID in " + lines))
        println("Created VM: " + image.vmName + " with UUID " + uuid)
        Some(RegisteredVm(image.vmName, uuid))

  def configureVm(vm: RegisteredVm, cpus: Int = 2, memory: Int = 4096, vram: Int = 128) =
    runVerbose(
      "modifyvm",
      vm.name,
      "--cpus",
      cpus.toString,
      "--memory",
      memory.toString,
      "--vram",
      vram.toString,
    )

  enum VmNetwork:
    case bridged(val adapter: String) extends VmNetwork
    case nat                          extends VmNetwork
    def commandArgs(): List[String] =
      this match
        case bridged(adapter) =>
          List("--nic1", "bridged", "--bridgeadapter1", adapter)
        case nat =>
          List("--nic1", "nat")

  def configureNetwork(vm: RegisteredVm, network: VmNetwork = VmNetwork.nat) =
    runVerbose("modifyvm" :: vm.name :: network.commandArgs()*)

  enum GraphicsController:
    case vmsvga extends GraphicsController
    def commandArgs(): List[String] =
      this match
        case vmsvga => List("--graphicscontroller", "vmsvga")
  def configureGraphics(
    vm: RegisteredVm,
    graphics: GraphicsController = GraphicsController.vmsvga,
    accelerated: Boolean = true,
  ) =
    runVerbose(
      "modifyvm" :: vm.name :: graphics.commandArgs()
        ++ ("--accelerate3d" :: (if accelerated then "on"
                                 else "off") :: Nil)*,
    )

  def attachDisk(vm: RegisteredVm, image: virtualBoxImage) =
    val diskPath = image.vmImage.vdiFilePath
    runVerbose(
      "storagectl",
      vm.name,
      "--name",
      "SATA Controller",
      "--add",
      "sata",
      "--controller",
      "IntelAHCI",
    )
    runVerbose(
      "storageattach",
      vm.name,
      "--storagectl",
      "SATA Controller",
      "--port",
      "0",
      "--device",
      "0",
      "--type",
      "hdd",
      "--medium",
      diskPath.toString(),
    )

  def configureDvd(vm: RegisteredVm) =
    runVerbose(
      "storagectl",
      vm.name,
      "--name",
      "IDE Controller",
      "--add",
      "ide",
      "--controller",
      "PIIX4",
    )
    runVerbose(
      "storageattach",
      vm.name,
      "--storagectl",
      "IDE Controller",
      "--port",
      "0",
      "--device",
      "0",
      "--type",
      "dvddrive",
      "--medium",
      "emptydrive",
    )

  def configureBootOrder(vm: RegisteredVm) =
    runVerbose(
      "modifyvm",
      vm.name,
      "--boot1",
      "disk",
      "--boot2",
      "dvd",
      "--boot3",
      "none",
      "--boot4",
      "none",
    )

  def detachDisk(vm: RegisteredVm) =
    runVerbose(
      "storageattach",
      vm.name,
      "--storagectl",
      "SATA Controller",
      "--port",
      "0",
      "--device",
      "0",
      "--type",
      "hdd",
      "--medium",
      "none",
    )

  def deleteVm(vm: RegisteredVm) =
    detachDisk(vm)
    runVerbose("unregistervm", vm.name, "--delete")

  def startVm(vm: RegisteredVm) =
    runVerbose("startvm", vm.name)

  def constructVm(image: virtualBoxImage): Option[RegisteredVm] =
    // TODO think if we should have all arguments passed here or if we can infer them based on the image and the host system
    createVm(image).map { vm =>
      configureVm(vm)
      configureNetwork(vm)
      configureGraphics(vm)
      attachDisk(vm, image)
      configureDvd(vm)
      configureBootOrder(vm)
      vm
    }

  def constructVmIfNeeded(image: virtualBoxImage): Option[RegisteredVm] =
    listVms().find(_.name == image.vmName) match
      case Some(vm) =>
        println("VM already created: " + vm)
        Some(vm)
      case None =>
        constructVm(image)

end virtualBox
