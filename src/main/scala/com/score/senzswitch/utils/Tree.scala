package com.score.senzswitch.utils

import java.io.{File, FileFilter, FilenameFilter}

sealed trait Tree[+A]

case class Leaf[A](value: A) extends Tree[A]

case class Node[A](nodes: Seq[Tree[A]]) extends Tree[A]

//object Main extends App {
//
//  val tree: Tree[String] = Node(
//    Seq(
//      Node(
//        Seq(Node(Seq(Leaf("A"), Leaf("B"))))
//      ),
//      Node(
//        Seq(Leaf("C"))
//      ),
//      Node(
//        Seq(Node(Seq(Leaf("D"), Leaf("E"))))
//      )
//    )
//  )
//
//  def traverse(tree: Tree[String]): Unit = {
//    tree match {
//      case Leaf(l) =>
//        println(l)
//      case Node(n) =>
//        for (i <- n) traverse(i)
//    }
//  }
//
//  def buildTree(path: String, name: String): Unit = {
//    def getName(curName: String, dirName: String) = {
//      if (curName.isEmpty) {
//        dirName
//      } else {
//        dirName match {
//          case "credit" =>
//            s"$curName:#credit"
//          case "debet" =>
//            s"$curName:#debet"
//          case _ =>
//            s"$curName:$dirName"
//        }
//      }
//    }
//
//    val file = new File(path)
//
//    val xslFilter = new FilenameFilter {
//      override def accept(dir: File, name: String): Boolean = name.toLowerCase.endsWith(".xsl")
//    }
//
//    val dirFilter = new FileFilter {
//      override def accept(pathname: File): Boolean = pathname.isDirectory
//    }
//
//    file.listFiles(xslFilter) match {
//      case Array() =>
//        // no xslt files, not in depth/not a leaf
//        // may be directory
//        for (f <- file.listFiles(dirFilter)) buildTree(f.getPath, getName(name, f.getName))
//      case nodes =>
//        // have xslt files, in depth/leaf
//        val node = Node(nodes.map(f => Leaf(f)))
//        //println(node)
//        println(name)
//    }
//  }
//
//  //traverse(tree)
//  buildTree("/Users/eranga/Desktop/schematrons", "")
//
//}